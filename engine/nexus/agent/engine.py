import json
import uuid
from typing import Any, Dict, List, Optional
from nexus.api.ws import ws_hub
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting
from nexus.memory.manager import MemoryManager
from nexus.permissions.manager import PermissionManager
from nexus.planner.planner import TaskPlanner
from nexus.providers.manager import ProviderManager
from nexus.tools.manager import ToolManager

class AgentEngine:
    @staticmethod
    def is_stopped() -> bool:
        return bool(get_setting("agent_stopped", False))

    @staticmethod
    async def stop_all_tasks() -> Dict[str, Any]:
        set_setting("agent_stopped", True)
        now = _utc_now()
        with db_session() as conn:
            cur = conn.execute(
                "UPDATE tasks SET status = 'cancelled', error_message = 'Dihentikan secara darurat oleh pengguna (Stop Agent).', updated_at = ? WHERE status IN ('pending','planning','running','verifying')",
                (now,),
            )
            cancelled_count = cur.rowcount
        log_activity("WARNING", "agent", "Emergency Stop Agent triggered by user", {"cancelled_tasks": cancelled_count})
        await ws_hub.broadcast("agent_stopped", {"stopped": True, "cancelled_tasks": cancelled_count, "timestamp": now})
        return {"agent_stopped": True, "cancelled_tasks": cancelled_count, "timestamp": now}

    @staticmethod
    async def resume_agent() -> Dict[str, Any]:
        set_setting("agent_stopped", False)
        now = _utc_now()
        log_activity("INFO", "agent", "Agent resumed by user")
        await ws_hub.broadcast("runtime_status", {"agent_stopped": False, "timestamp": now})
        return {"agent_stopped": False, "timestamp": now}

    @staticmethod
    async def cancel_task(task_id: str) -> Optional[Dict[str, Any]]:
        now = _utc_now()
        with db_session() as conn:
            row = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            if not row:
                return None
            conn.execute(
                "UPDATE tasks SET status = 'cancelled', error_message = 'Dibatalkan oleh pengguna.', updated_at = ? WHERE id = ?",
                (now, task_id),
            )
            conn.execute(
                "UPDATE task_steps SET status = 'cancelled', updated_at = ? WHERE task_id = ? AND status IN ('pending','running')",
                (now, task_id),
            )
        await ws_hub.broadcast("task_progress", {"task_id": task_id, "status": "cancelled"})
        return AgentEngine.get_task(task_id)

    @staticmethod
    def get_task(task_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            t = conn.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()
            if not t:
                return None
            steps = conn.execute("SELECT * FROM task_steps WHERE task_id = ? ORDER BY step_number ASC", (task_id,)).fetchall()
            calls = conn.execute("SELECT * FROM tool_calls WHERE task_id = ? ORDER BY created_at ASC", (task_id,)).fetchall()
            approvals = conn.execute("SELECT * FROM approvals WHERE task_id = ? ORDER BY requested_at DESC", (task_id,)).fetchall()
            d = dict(t)
            d["result_summary"] = d.get("result_summary") or ""
            d["error_message"] = d.get("error_message") or ""
            d["recovery_steps"] = d.get("recovery_steps") or ""
            d["plan"] = json.loads(d.pop("plan_json", "[]"))
            d["steps"] = [dict(s) for s in steps]
            d["tool_calls"] = [dict(c) for c in calls]
            d["approvals"] = [dict(a) for a in approvals]
            return d

    @staticmethod
    def list_tasks(limit: int = 50) -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM tasks ORDER BY created_at DESC LIMIT ?", (limit,)).fetchall()
            out = []
            for r in rows:
                d = dict(r)
                d["result_summary"] = d.get("result_summary") or ""
                d["error_message"] = d.get("error_message") or ""
                d["recovery_steps"] = d.get("recovery_steps") or ""
                d["plan"] = json.loads(d.pop("plan_json", "[]"))
                out.append(d)
            return out

    @staticmethod
    async def run_workflow(
        prompt: str,
        conversation_id: Optional[str] = None,
        project_id: Optional[str] = "proj-default",
        preferred_model_id: Optional[str] = None,
        requested_runtime: Optional[str] = None,
        bypass_approval: bool = False,
    ) -> Dict[str, Any]:
        if AgentEngine.is_stopped():
            raise RuntimeError("Agent sedang dalam status STOP. Aktifkan kembali Agent sebelum mengirim tugas baru.")

        now = _utc_now()
        if not conversation_id:
            conversation_id = f"conv-{uuid.uuid4().hex[:10]}"
            with db_session() as conn:
                conn.execute(
                    "INSERT INTO conversations (id, title, project_id, runtime, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    (conversation_id, prompt[:60], project_id, requested_runtime or "local", now, now),
                )
        else:
            with db_session() as conn:
                exists = conn.execute("SELECT id FROM conversations WHERE id = ?", (conversation_id,)).fetchone()
                if not exists:
                    conn.execute(
                        "INSERT INTO conversations (id, title, project_id, runtime, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                        (conversation_id, prompt[:60], project_id, requested_runtime or "local", now, now),
                    )

        # Save user message
        user_msg_id = f"msg-{uuid.uuid4().hex[:10]}"
        with db_session() as conn:
            conn.execute(
                "INSERT INTO messages (id, conversation_id, role, content, metadata_json, created_at) VALUES (?, ?, 'user', ?, '{}', ?)",
                (user_msg_id, conversation_id, prompt, now),
            )

        # Step 1 & 2: Understand & Plan
        analysis = TaskPlanner.analyze_intent(prompt, requested_runtime=requested_runtime)
        task_id = f"task-{uuid.uuid4().hex[:10]}"
        selected_model = ProviderManager.select_model_for_task(
            task_type=analysis["task_type"], preferred_model_id=preferred_model_id
        )

        with db_session() as conn:
            conn.execute(
                """INSERT INTO tasks
                (id, title, prompt, conversation_id, project_id, runtime, status, risk_level, model_id, plan_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'running', ?, ?, ?, ?, ?)""",
                (
                    task_id,
                    prompt[:70],
                    prompt,
                    conversation_id,
                    project_id,
                    analysis["selected_runtime"],
                    analysis["risk_level"],
                    selected_model["id"],
                    json.dumps(analysis["steps"]),
                    now,
                    now,
                ),
            )
            step_ids = {}
            for s in analysis["steps"]:
                sid = f"step-{uuid.uuid4().hex[:10]}"
                step_ids[s["phase"]] = sid
                conn.execute(
                    """INSERT INTO task_steps
                    (id, task_id, step_number, phase, title, description, tool_name, risk_level, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?, ?)""",
                    (
                        sid,
                        task_id,
                        s["step_number"],
                        s["phase"],
                        s["title"],
                        s["description"],
                        s["tool_name"],
                        s["risk_level"],
                        now,
                        now,
                    ),
                )

        async def _complete_step(phase_key: str, output_text: str, status_val: str = "completed"):
            sid = step_ids[phase_key]
            ts = _utc_now()
            with db_session() as conn:
                conn.execute(
                    "UPDATE task_steps SET status = ?, output = ?, updated_at = ? WHERE id = ?",
                    (status_val, output_text, ts, sid),
                )
            await ws_hub.broadcast("task_progress", {
                "task_id": task_id,
                "phase": phase_key,
                "status": status_val,
                "output": output_text,
            })

        # Phase 1: Understand
        await _complete_step("understand", f"Intent={analysis['task_type']}, Risk={analysis['risk_level']} ({analysis['risk_reason']})")
        # Phase 2: Plan
        await _complete_step("plan", f"Disusun 8 fase kerja; Tool utama={analysis['planned_tool'] or 'AI Reasoning'}")
        # Phase 3: Select Runtime
        await _complete_step("select_runtime", f"Runtime={analysis['selected_runtime']} — {analysis['runtime_note']}")
        # Phase 4: Select Model
        await _complete_step("select_model", f"Model={selected_model['display_name']} ({selected_model['model_name']})")

        # Check Critical / High Risk before Phase 5 (Execute)
        if analysis["risk_level"] in ("high", "critical") and not bypass_approval:
            appr = PermissionManager.create_approval_request(
                task_id=task_id,
                step_id=step_ids["execute"],
                action_type=analysis["planned_tool"] or "critical_command",
                risk_level=analysis["risk_level"],
                description=analysis["risk_reason"],
                payload={"prompt": prompt, "tool": analysis["planned_tool"], "args": analysis["planned_args"]},
            )
            await _complete_step("execute", f"Menunggu persetujuan pengguna (Approval ID: {appr['id']})", status_val="awaiting_approval")
            with db_session() as conn:
                conn.execute(
                    "UPDATE tasks SET status = 'awaiting_approval', result_summary = ?, updated_at = ? WHERE id = ?",
                    (f"Tugas dijeda karena risiko {analysis['risk_level'].upper()}: {analysis['risk_reason']}", _utc_now(), task_id),
                )
            await ws_hub.broadcast("approval_requested", appr)
            return AgentEngine.get_task(task_id)

        # Phase 5: Execute
        exec_summary = ""
        tool_failed = False
        if analysis["planned_tool"]:
            tool_res = ToolManager.execute_tool(
                analysis["planned_tool"],
                analysis["planned_args"],
                task_id=task_id,
                step_id=step_ids["execute"],
                bypass_approval=bypass_approval,
            )
            if tool_res["status"] == "awaiting_approval":
                with db_session() as conn:
                    conn.execute("UPDATE tasks SET status = 'awaiting_approval', updated_at = ? WHERE id = ?", (_utc_now(), task_id))
                await _complete_step("execute", "Menunggu persetujuan pengguna di Approval Center.", status_val="awaiting_approval")
                return AgentEngine.get_task(task_id)
            elif tool_res["status"] == "failed":
                tool_failed = True
                exec_summary = f"Tool {analysis['planned_tool']} gagal: {json.dumps(tool_res['output'])}"
                await _complete_step("execute", exec_summary, status_val="failed")
            else:
                exec_summary = f"Tool {analysis['planned_tool']} berhasil dijalankan: {json.dumps(tool_res['output'])[:600]}"
                await _complete_step("execute", exec_summary, status_val="completed")
                if analysis["planned_tool"] == "terminal_exec":
                    await ws_hub.broadcast("terminal_output", tool_res["output"])
        else:
            exec_summary = "Menjalankan penalaran kontekstual AI Agent."
            await _complete_step("execute", exec_summary, status_val="completed")

        if tool_failed:
            recovery = (
                "1. Periksa apakah path file berada di dalam folder workspace yang diizinkan.\n"
                "2. Periksa argumen perintah atau gunakan fitur Rollback jika ada file yang berubah.\n"
                "3. Coba jalankan kembali tugas setelah memperbaiki parameter."
            )
            with db_session() as conn:
                conn.execute(
                    "UPDATE tasks SET status = 'failed', error_message = ?, recovery_steps = ?, updated_at = ? WHERE id = ?",
                    (exec_summary, recovery, _utc_now(), task_id),
                )
            await _complete_step("verify", "Verifikasi mendeteksi kegagalan pada tahap eksekusi.", status_val="failed")
            await _complete_step("report", f"Tugas gagal. Langkah pemulihan:\n{recovery}", status_val="failed")
            return AgentEngine.get_task(task_id)

        # Phase 6: Verify
        await _complete_step("verify", "Hasil eksekusi terverifikasi valid dan sesuai kebijakan keamanan.")

        # Phase 7: Remember
        if get_setting("memory_store_tasks", True):
            MemoryManager.store_memory(
                category="task",
                key=f"task:{task_id}",
                content=f"Perintah: {prompt} | Hasil: {exec_summary[:300]}",
                project_id=project_id,
                importance=3,
            )
        await _complete_step("remember", "Konteks tugas disimpan ke SQLite Memory (FTS5).")

        # Phase 8: Report (AI Completion + Streaming chunks via WebSocket)
        ai_reply, used_model = await ProviderManager.generate_completion(
            prompt=prompt,
            system_instruction="Kamu adalah NEXUS AI, personal Android AI Agent dengan arsitektur hybrid local-cloud.",
            preferred_model_id=selected_model["id"],
            task_type=analysis["task_type"],
            context_summary=exec_summary,
        )
        # Stream chunks to WebSocket listeners
        words = ai_reply.split(" ")
        chunk_size = max(1, len(words) // 4)
        for i in range(0, len(words), chunk_size):
            chunk = " ".join(words[i:i + chunk_size])
            await ws_hub.broadcast("chat_stream", {
                "conversation_id": conversation_id,
                "task_id": task_id,
                "delta": chunk + " ",
                "done": (i + chunk_size >= len(words)),
            })

        await _complete_step("report", ai_reply[:1000])

        assistant_msg_id = f"msg-{uuid.uuid4().hex[:10]}"
        finish_ts = _utc_now()
        with db_session() as conn:
            conn.execute(
                "INSERT INTO messages (id, conversation_id, role, content, model_used, provider_id, metadata_json, created_at) VALUES (?, ?, 'assistant', ?, ?, ?, ?, ?)",
                (
                    assistant_msg_id,
                    conversation_id,
                    ai_reply,
                    used_model["model_name"],
                    used_model["provider_id"],
                    json.dumps({"task_id": task_id, "runtime": analysis["selected_runtime"]}),
                    finish_ts,
                ),
            )
            conn.execute(
                "UPDATE conversations SET updated_at = ? WHERE id = ?",
                (finish_ts, conversation_id),
            )
            conn.execute(
                "UPDATE tasks SET status = 'completed', result_summary = ?, updated_at = ? WHERE id = ?",
                (ai_reply, finish_ts, task_id),
            )

        log_activity("INFO", "agent", f"Completed task {task_id}", {"runtime": analysis["selected_runtime"], "model": used_model["model_name"]})
        return AgentEngine.get_task(task_id)
