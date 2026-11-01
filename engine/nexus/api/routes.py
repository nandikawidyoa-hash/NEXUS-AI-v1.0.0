import os
import platform
from typing import Any, Dict, List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

from nexus import __app_name__, __version__
from nexus.agent.engine import AgentEngine
from nexus.agent.hybrid import HybridRuntimeManager
from nexus.automation.scheduler import AutomationScheduler
from nexus.config import HOST, PORT, WORKSPACE_DIR
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting
from nexus.memory.manager import MemoryManager
from nexus.permissions.manager import PermissionManager
from nexus.providers.manager import ProviderManager
from nexus.security.auth import require_auth
from nexus.skills.manager import SkillManager
from nexus.tools.manager import ToolManager

router = APIRouter(dependencies=[Depends(require_auth)])

class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1)
    conversation_id: Optional[str] = None
    project_id: Optional[str] = "proj-default"
    model_id: Optional[str] = None
    runtime: Optional[str] = None

class TaskCreateRequest(BaseModel):
    prompt: str = Field(..., min_length=1)
    conversation_id: Optional[str] = None
    project_id: Optional[str] = "proj-default"
    model_id: Optional[str] = None
    runtime: Optional[str] = "local"

class ProviderCreateRequest(BaseModel):
    name: str
    provider_type: str
    base_url: str
    api_key: str = ""
    priority: int = 10
    rate_limit_rpm: int = 60
    default_model_name: Optional[str] = None

class ProviderKeyUpdateRequest(BaseModel):
    api_key: str
    base_url: Optional[str] = None
    enabled: Optional[bool] = True
    model_name: Optional[str] = None

class ProviderTestRequest(BaseModel):
    provider_id: str

class SkillInstallRequest(BaseModel):
    folder_name: str
    skill_md_content: str = ""
    enable_immediately: bool = True

class SkillToggleRequest(BaseModel):
    enabled: bool

class AutomationCreateRequest(BaseModel):
    name: str
    prompt: str
    description: str = ""
    schedule_cron: str = "0 * * * *"
    interval_seconds: int = 3600
    runtime: str = "local"
    max_retries: int = 3

class ApprovalDecisionRequest(BaseModel):
    approved: bool
    note: str = ""

class MemoryCreateRequest(BaseModel):
    category: str = "long_term"
    key: str
    content: str
    project_id: Optional[str] = "proj-default"
    importance: int = 3

class MemoryUpdateRequest(BaseModel):
    key: Optional[str] = None
    content: Optional[str] = None
    importance: Optional[int] = None

class ToolExecRequest(BaseModel):
    tool_name: str
    arguments: Dict[str, Any] = {}

class RollbackRequest(BaseModel):
    backup_path: str
    original_path: str

class CloudConfigRequest(BaseModel):
    cloud_url: str
    enabled: bool
    active_runtime: str = "local"

class DeviceStateReportRequest(BaseModel):
    battery_percent: int
    is_charging: bool
    network_type: str = "wifi"
    granted_permissions: List[str] = []
    saf_directories: List[str] = []

@router.get("/health")
async def get_health():
    return {
        "status": "ok",
        "service": __app_name__,
        "version": __version__,
        "agent_stopped": AgentEngine.is_stopped(),
        "timestamp": _utc_now(),
    }

@router.get("/system/info")
async def get_system_info():
    is_termux = "com.termux" in os.environ.get("PREFIX", "") or os.path.exists("/data/data/com.termux")
    with db_session() as conn:
        conv_count = conn.execute("SELECT COUNT(*) as c FROM conversations").fetchone()["c"]
        task_count = conn.execute("SELECT COUNT(*) as c FROM tasks").fetchone()["c"]
        mem_count = conn.execute("SELECT COUNT(*) as c FROM memories").fetchone()["c"]
        pending_approvals = conn.execute("SELECT COUNT(*) as c FROM approvals WHERE status = 'pending'").fetchone()["c"]
    return {
        "app": __app_name__,
        "version": __version__,
        "platform": platform.platform(),
        "python_version": platform.python_version(),
        "is_termux_environment": is_termux,
        "local_endpoint": f"http://{HOST}:{PORT}",
        "workspace_dir": str(WORKSPACE_DIR),
        "agent_stopped": AgentEngine.is_stopped(),
        "hybrid_runtime": HybridRuntimeManager.get_status(),
        "device_telemetry": get_setting("device_telemetry", {"battery_percent": 100, "is_charging": True}),
        "counts": {
            "conversations": conv_count,
            "tasks": task_count,
            "memories": mem_count,
            "pending_approvals": pending_approvals,
        },
    }

@router.post("/chat")
async def post_chat(req: ChatRequest):
    task = await AgentEngine.run_workflow(
        prompt=req.message,
        conversation_id=req.conversation_id,
        project_id=req.project_id,
        preferred_model_id=req.model_id,
        requested_runtime=req.runtime,
    )
    return {
        "conversation_id": task["conversation_id"],
        "task_id": task["id"],
        "status": task["status"],
        "reply": task["result_summary"] or task["error_message"] or "Tugas sedang menunggu persetujuan.",
        "task": task,
    }

@router.get("/conversations")
async def get_conversations():
    with db_session() as conn:
        rows = conn.execute("SELECT * FROM conversations ORDER BY updated_at DESC").fetchall()
        return {"conversations": [dict(r) for r in rows]}

@router.get("/conversations/{conversation_id}")
async def get_conversation_detail(conversation_id: str):
    with db_session() as conn:
        conv = conn.execute("SELECT * FROM conversations WHERE id = ?", (conversation_id,)).fetchone()
        if not conv:
            raise HTTPException(status_code=404, detail="Conversation not found")
        msgs = conn.execute("SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC", (conversation_id,)).fetchall()
        return {"conversation": dict(conv), "messages": [dict(m) for m in msgs]}

@router.get("/tasks")
async def get_tasks():
    return {"tasks": AgentEngine.list_tasks()}

@router.post("/tasks")
async def create_task(req: TaskCreateRequest):
    task = await AgentEngine.run_workflow(
        prompt=req.prompt,
        conversation_id=req.conversation_id,
        project_id=req.project_id,
        preferred_model_id=req.model_id,
        requested_runtime=req.runtime,
    )
    return {"task": task}

@router.get("/tasks/{task_id}")
async def get_task_detail(task_id: str):
    task = AgentEngine.get_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return {"task": task}

@router.post("/tasks/{task_id}/cancel")
async def cancel_task_endpoint(task_id: str):
    task = await AgentEngine.cancel_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return {"task": task}

@router.post("/agent/stop")
async def stop_agent_endpoint():
    return await AgentEngine.stop_all_tasks()

@router.post("/agent/resume")
async def resume_agent_endpoint():
    return await AgentEngine.resume_agent()

@router.get("/providers")
async def get_providers():
    return {"providers": ProviderManager.list_providers()}

@router.post("/providers")
async def add_provider_endpoint(req: ProviderCreateRequest):
    prov = ProviderManager.add_provider(
        name=req.name,
        provider_type=req.provider_type,
        base_url=req.base_url,
        api_key=req.api_key,
        priority=req.priority,
        rate_limit_rpm=req.rate_limit_rpm,
        default_model_name=req.default_model_name,
    )
    return {"provider": prov}

@router.put("/providers/{provider_id}/key")
async def update_provider_key_endpoint(provider_id: str, req: ProviderKeyUpdateRequest):
    prov = ProviderManager.update_api_key(provider_id, req.api_key, req.base_url, req.enabled, req.model_name)
    if not prov:
        raise HTTPException(status_code=404, detail="Provider not found")
    return {"provider": prov}

@router.delete("/providers/{provider_id}")
async def delete_provider_endpoint(provider_id: str):
    ok = ProviderManager.delete_provider(provider_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Provider not found")
    return {"deleted": True}

@router.post("/providers/test")
async def test_provider_endpoint(req: ProviderTestRequest):
    return await ProviderManager.test_provider_connection(req.provider_id)

@router.get("/models")
async def get_models():
    return {
        "auto_model_routing": get_setting("auto_model_routing", True),
        "models": ProviderManager.list_models(),
    }

@router.post("/models/{model_id}/select")
async def select_default_model_endpoint(model_id: str):
    ok = ProviderManager.set_default_model(model_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Model not found")
    return {"selected_model_id": model_id, "auto_model_routing": False}

@router.post("/models/routing")
async def set_auto_routing_endpoint(enabled: bool = Query(True)):
    set_setting("auto_model_routing", enabled)
    return {"auto_model_routing": enabled}

@router.get("/tools")
async def get_tools():
    return {"tools": ToolManager.list_tools()}

@router.post("/tools/execute")
async def execute_tool_endpoint(req: ToolExecRequest):
    return ToolManager.execute_tool(req.tool_name, req.arguments)

@router.post("/tools/rollback")
async def rollback_file_endpoint(req: RollbackRequest):
    try:
        return ToolManager.rollback_from_backup(req.backup_path, req.original_path)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.get("/skills")
async def get_skills(scan: bool = Query(False)):
    if scan:
        SkillManager.scan_skills_directory()
    return {"skills": SkillManager.list_skills()}

@router.post("/skills/scan")
async def scan_skills_endpoint():
    return SkillManager.scan_skills_directory()

@router.get("/skills/{skill_id}/raw")
async def get_skill_raw_endpoint(skill_id: str):
    info = SkillManager.get_skill_raw(skill_id)
    if not info:
        raise HTTPException(status_code=404, detail="Skill not found")
    return info

@router.post("/skills/install")
async def install_skill_endpoint(req: SkillInstallRequest):
    try:
        skill = SkillManager.install_skill(req.folder_name, req.skill_md_content, req.enable_immediately)
        return {"skill": skill}
    except PermissionError as pe:
        raise HTTPException(status_code=403, detail=str(pe))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.post("/skills/{skill_id}/install")
async def install_discovered_skill_endpoint(skill_id: str, enable: bool = Query(True)):
    try:
        skill = SkillManager.install_discovered_skill(skill_id, enable_immediately=enable)
        return {"skill": skill}
    except PermissionError as pe:
        raise HTTPException(status_code=403, detail=str(pe))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.post("/skills/{skill_id}/enable")
async def enable_skill_endpoint(skill_id: str):
    try:
        updated = SkillManager.enable_skill(skill_id)
        if not updated:
            raise HTTPException(status_code=404, detail="Skill not found")
        return {"skill": updated}
    except PermissionError as pe:
        raise HTTPException(status_code=403, detail=str(pe))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.post("/skills/{skill_id}/disable")
async def disable_skill_endpoint(skill_id: str):
    try:
        updated = SkillManager.disable_skill(skill_id)
        if not updated:
            raise HTTPException(status_code=404, detail="Skill not found")
        return {"skill": updated}
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.post("/skills/{skill_id}/toggle")
async def toggle_skill_endpoint(skill_id: str, req: SkillToggleRequest):
    try:
        updated = SkillManager.toggle_skill(skill_id, req.enabled)
        if not updated:
            raise HTTPException(status_code=404, detail="Skill not found")
        return {"skill": updated}
    except PermissionError as pe:
        raise HTTPException(status_code=403, detail=str(pe))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.post("/skills/{skill_id}/uninstall")
async def uninstall_skill_endpoint(skill_id: str, delete_files: bool = Query(False)):
    try:
        return SkillManager.uninstall_skill(skill_id, delete_files=delete_files)
    except Exception as exc:
        raise HTTPException(status_code=400, detail=str(exc))

@router.delete("/skills/{skill_id}")
async def delete_skill_endpoint(skill_id: str):
    try:
        ok = SkillManager.delete_skill(skill_id)
        if not ok:
            raise HTTPException(status_code=404, detail="Skill not found")
        return {"deleted": True}
    except PermissionError as pe:
        raise HTTPException(status_code=403, detail=str(pe))

@router.get("/memories")
async def get_memories(category: Optional[str] = None, project_id: Optional[str] = None):
    return {"memories": MemoryManager.list_memories(category=category, project_id=project_id)}

@router.get("/memories/search")
async def search_memories_endpoint(q: str = Query(""), category: Optional[str] = None):
    return {"query": q, "results": MemoryManager.search_memories(q, category=category)}

@router.post("/memories")
async def create_memory_endpoint(req: MemoryCreateRequest):
    mem = MemoryManager.store_memory(req.category, req.key, req.content, req.project_id, req.importance)
    return {"memory": mem}

@router.put("/memories/{memory_id}")
async def update_memory_endpoint(memory_id: str, req: MemoryUpdateRequest):
    updated = MemoryManager.update_memory(memory_id, req.key, req.content, req.importance)
    if not updated:
        raise HTTPException(status_code=404, detail="Memory not found")
    return {"memory": updated}

@router.delete("/memories/{memory_id}")
async def delete_memory_endpoint(memory_id: str):
    ok = MemoryManager.delete_memory(memory_id)
    if not ok:
        raise HTTPException(status_code=404, detail="Memory not found")
    return {"deleted": True}

@router.get("/automations")
async def get_automations():
    return {"automations": AutomationScheduler.list_automations()}

@router.post("/automations")
async def create_automation_endpoint(req: AutomationCreateRequest):
    auto = AutomationScheduler.create_automation(
        name=req.name,
        prompt=req.prompt,
        description=req.description,
        schedule_cron=req.schedule_cron,
        interval_seconds=req.interval_seconds,
        runtime=req.runtime,
        max_retries=req.max_retries,
    )
    return {"automation": auto}

@router.post("/automations/{automation_id}/run")
async def run_automation_now_endpoint(automation_id: str):
    res = await AutomationScheduler.run_automation_now(automation_id)
    if not res:
        raise HTTPException(status_code=404, detail="Automation not found")
    return res

@router.get("/approvals")
async def get_approvals(status_filter: Optional[str] = None):
    with db_session() as conn:
        if status_filter:
            rows = conn.execute("SELECT * FROM approvals WHERE status = ? ORDER BY requested_at DESC", (status_filter,)).fetchall()
        else:
            rows = conn.execute("SELECT * FROM approvals ORDER BY requested_at DESC").fetchall()
        return {"approvals": [dict(r) for r in rows]}

@router.post("/approvals/{approval_id}")
async def resolve_approval_endpoint(approval_id: str, req: ApprovalDecisionRequest):
    resolved = PermissionManager.resolve_approval(approval_id, req.approved, req.note)
    if not resolved:
        raise HTTPException(status_code=404, detail="Approval request not found")
    return {"approval": resolved}

@router.get("/logs")
async def get_activity_logs(limit: int = 100):
    with db_session() as conn:
        rows = conn.execute("SELECT * FROM activity_logs ORDER BY created_at DESC LIMIT ?", (limit,)).fetchall()
        return {"logs": [dict(r) for r in rows]}

@router.get("/cloud/status")
async def get_cloud_runtime_status():
    return HybridRuntimeManager.get_status()

@router.post("/cloud/config")
async def configure_cloud_runtime(req: CloudConfigRequest):
    return HybridRuntimeManager.configure_cloud(req.cloud_url, req.enabled, req.active_runtime)

@router.post("/cloud/sync")
async def sync_cloud_runtime():
    return HybridRuntimeManager.synchronize_state()

@router.post("/device/report")
async def report_device_telemetry(req: DeviceStateReportRequest):
    telemetry = req.dict()
    telemetry["reported_at"] = _utc_now()
    set_setting("device_telemetry", telemetry)
    return {"updated": True, "telemetry": telemetry}
