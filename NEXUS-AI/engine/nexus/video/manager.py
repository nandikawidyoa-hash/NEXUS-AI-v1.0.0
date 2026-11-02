import json
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional
import httpx

from nexus.config import WORKSPACE_DIR
from nexus.database.db import _utc_now, db_session, get_setting, log_activity, set_setting
from nexus.security.crypto import decrypt_secret, encrypt_secret, mask_api_key

VIDEO_PROVIDERS_CATALOG = [
    {
        "id": "video-siliconflow",
        "name": "SiliconFlow (Wan 2.1 — Free Tier / Low Cost)",
        "provider_type": "siliconflow",
        "base_url": "https://api.siliconflow.cn/v1",
        "models": [
            {"id": "Wan-AI/Wan2.1-T2V-14B", "mode": "text_to_video", "label": "Wan 2.1 T2V 14B (Text-to-Video)"},
            {"id": "Wan-AI/Wan2.1-I2V-14B-720P", "mode": "image_to_video", "label": "Wan 2.1 I2V 14B 720P (Image-to-Video)"},
        ],
        "supported_ratios": ["16:9", "9:16", "1:1"],
        "supported_resolutions": ["720p"],
        "supported_durations": [5],
        "pricing_note": "Menyediakan kredit trial gratis saat registrasi & biaya sangat rendah per video.",
    },
    {
        "id": "video-dashscope",
        "name": "Alibaba Cloud DashScope (Wanx 2.1 Official)",
        "provider_type": "dashscope",
        "base_url": "https://dashscope-intl.aliyuncs.com/api/v1",
        "models": [
            {"id": "wan2.1-t2v-turbo", "mode": "text_to_video", "label": "Wan 2.1 T2V Turbo (Cepat & Hemat)"},
            {"id": "wan2.1-t2v-plus", "mode": "text_to_video", "label": "Wan 2.1 T2V Plus (Kualitas Tinggi)"},
            {"id": "wan2.1-i2v-turbo", "mode": "image_to_video", "label": "Wan 2.1 I2V Turbo (Image-to-Video)"},
        ],
        "supported_ratios": ["16:9", "9:16", "1:1"],
        "supported_resolutions": ["480p", "720p", "1080p"],
        "supported_durations": [5],
        "pricing_note": "Mendukung kuota gratis pengguna baru Alibaba Model Studio (DashScope).",
    },
    {
        "id": "video-replicate",
        "name": "Replicate API (Wan 2.1 & Minimax Video-01)",
        "provider_type": "replicate",
        "base_url": "https://api.replicate.com/v1",
        "models": [
            {"id": "wan-video/wan-2.1-1.3b", "mode": "text_to_video", "label": "Wan 2.1 1.3B (Ekonomis & Cepat)"},
            {"id": "minimax/video-01", "mode": "text_to_video", "label": "Minimax Hailuo Video-01 (T2V / I2V)"},
        ],
        "supported_ratios": ["16:9", "9:16", "1:1"],
        "supported_resolutions": ["480p", "720p"],
        "supported_durations": [5, 6],
        "pricing_note": "Pay-per-second (~$0.03 - $0.10 per video), tanpa biaya langganan bulanan.",
    },
]

RESOLUTION_MAP_DASHSCOPE = {
    ("16:9", "480p"): "832*480",
    ("9:16", "480p"): "480*832",
    ("1:1", "480p"): "624*624",
    ("16:9", "720p"): "1280*720",
    ("9:16", "720p"): "720*1280",
    ("1:1", "720p"): "960*960",
    ("16:9", "1080p"): "1920*1080",
    ("9:16", "1080p"): "1080*1920",
    ("1:1", "1080p"): "1080*1080",
}


class VideoGenerationManager:
    @staticmethod
    def enhance_prompt(raw_prompt: str, mode: str = "text_to_video", aspect_ratio: str = "16:9") -> str:
        clean = (raw_prompt or "").strip()
        if not clean:
            return ""
        framing = "vertical 9:16 mobile framing" if aspect_ratio == "9:16" else (
            "square 1:1 balanced composition" if aspect_ratio == "1:1" else "widescreen 16:9 cinematic framing"
        )
        motion = "subtle natural subject motion and smooth camera parallax" if mode == "image_to_video" else (
            "smooth cinematic camera movement, realistic physics, coherent temporal motion"
        )
        return (
            f"{clean}. Style: high detail, photorealistic lighting, {framing}, "
            f"{motion}, 4k clarity, crisp focus, rich atmosphere."
        )

    @staticmethod
    def get_providers_config() -> List[Dict[str, Any]]:
        saved_keys = get_setting("video_provider_keys", {}) or {}
        saved_urls = get_setting("video_provider_urls", {}) or {}
        out = []
        for p in VIDEO_PROVIDERS_CATALOG:
            pid = p["id"]
            enc_key = saved_keys.get(pid, "")
            raw_key = decrypt_secret(enc_key) if enc_key else ""
            item = dict(p)
            item["base_url"] = saved_urls.get(pid) or p["base_url"]
            item["has_api_key"] = bool(raw_key)
            item["key_masked"] = mask_api_key(raw_key) if raw_key else "Belum diatur"
            out.append(item)
        return out

    @staticmethod
    def configure_provider_key(provider_id: str, api_key: str, base_url: Optional[str] = None) -> Dict[str, Any]:
        saved_keys = get_setting("video_provider_keys", {}) or {}
        saved_urls = get_setting("video_provider_urls", {}) or {}
        clean_key = (api_key or "").strip()
        if clean_key:
            saved_keys[provider_id] = encrypt_secret(clean_key)
        if base_url and base_url.strip():
            saved_urls[provider_id] = base_url.strip().rstrip("/")
        set_setting("video_provider_keys", saved_keys)
        set_setting("video_provider_urls", saved_urls)
        log_activity("INFO", "video", f"Updated video provider key for {provider_id}")
        for p in VideoGenerationManager.get_providers_config():
            if p["id"] == provider_id:
                return p
        return {"id": provider_id, "key_masked": mask_api_key(clean_key) if clean_key else "Belum diatur"}

    @staticmethod
    def _get_raw_key_and_url(provider_id: str) -> tuple[str, str]:
        saved_keys = get_setting("video_provider_keys", {}) or {}
        saved_urls = get_setting("video_provider_urls", {}) or {}
        enc = saved_keys.get(provider_id, "")
        raw_key = decrypt_secret(enc) if enc else ""
        # Fallback: if user selects DashScope and already configured Alibaba Qwen in providers table, reuse it
        if not raw_key and provider_id == "video-dashscope":
            with db_session() as conn:
                row = conn.execute("SELECT encrypted_api_key FROM providers WHERE id = 'prov-qwen'").fetchone()
                if row and row["encrypted_api_key"]:
                    raw_key = decrypt_secret(row["encrypted_api_key"])
        default_url = "https://api.siliconflow.cn/v1"
        for p in VIDEO_PROVIDERS_CATALOG:
            if p["id"] == provider_id:
                default_url = p["base_url"]
                break
        return raw_key, (saved_urls.get(provider_id) or default_url).rstrip("/")

    @staticmethod
    def list_jobs(limit: int = 30) -> List[Dict[str, Any]]:
        with db_session() as conn:
            rows = conn.execute("SELECT * FROM video_jobs ORDER BY created_at DESC LIMIT ?", (limit,)).fetchall()
            return [dict(r) for r in rows]

    @staticmethod
    def get_job(job_id: str) -> Optional[Dict[str, Any]]:
        with db_session() as conn:
            row = conn.execute("SELECT * FROM video_jobs WHERE id = ?", (job_id,)).fetchone()
            return dict(row) if row else None

    @staticmethod
    async def create_video_job(
        prompt: str,
        provider_id: str = "video-siliconflow",
        model_name: str = "Wan-AI/Wan2.1-T2V-14B",
        mode: str = "text_to_video",
        image_url: str = "",
        duration_sec: int = 5,
        aspect_ratio: str = "16:9",
        resolution: str = "720p",
        use_prompt_enhancement: bool = True,
        api_key_override: str = "",
    ) -> Dict[str, Any]:
        clean_prompt = (prompt or "").strip()
        if not clean_prompt:
            raise ValueError("Prompt deskripsi video tidak boleh kosong.")
        if mode == "image_to_video" and not (image_url or "").strip():
            raise ValueError("Mode Image-to-Video membutuhkan URL gambar atau path gambar sumber.")

        enhanced = VideoGenerationManager.enhance_prompt(clean_prompt, mode, aspect_ratio) if use_prompt_enhancement else clean_prompt
        raw_key, base_url = VideoGenerationManager._get_raw_key_and_url(provider_id)
        if (api_key_override or "").strip():
            raw_key = api_key_override.strip()
            VideoGenerationManager.configure_provider_key(provider_id, raw_key)

        job_id = f"vid-{uuid.uuid4().hex[:10]}"
        now = _utc_now()
        prov_name = next((p["name"] for p in VIDEO_PROVIDERS_CATALOG if p["id"] == provider_id), provider_id)

        if not raw_key:
            err_msg = (
                f"API Key untuk '{prov_name}' belum diatur. Silakan isi API Key di pengaturan Generative Video AI "
                f"sebelum menjalankan rendering. (NEXUS AI tidak membuat hasil simulasi/palsu tanpa koneksi provider nyata)."
            )
            with db_session() as conn:
                conn.execute(
                    """INSERT INTO video_jobs
                    (id, provider_id, provider_name, model_name, mode, prompt, enhanced_prompt, image_url,
                     duration_sec, aspect_ratio, resolution, remote_job_id, status, progress_percent,
                     video_url, local_file_path, error_message, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', 'requires_api_key', 0, '', '', ?, ?, ?)""",
                    (
                        job_id, provider_id, prov_name, model_name, mode, clean_prompt, enhanced,
                        image_url or "", duration_sec, aspect_ratio, resolution, err_msg, now, now
                    ),
                )
            return VideoGenerationManager.get_job(job_id)

        # Submit real job to the selected video provider
        remote_id = ""
        status_val = "processing"
        progress_val = 15
        video_url_out = ""
        error_msg = ""

        try:
            async with httpx.AsyncClient(timeout=25.0, follow_redirects=True) as client:
                if provider_id == "video-dashscope":
                    url = f"{base_url}/services/aigc/video-generation/video-synthesis"
                    size_str = RESOLUTION_MAP_DASHSCOPE.get((aspect_ratio, resolution), "1280*720")
                    input_obj: Dict[str, Any] = {"prompt": enhanced}
                    if mode == "image_to_video" and image_url:
                        input_obj["img_url"] = image_url
                    payload = {
                        "model": model_name,
                        "input": input_obj,
                        "parameters": {"size": size_str, "duration": duration_sec, "prompt_extend": True},
                    }
                    headers = {
                        "Authorization": f"Bearer {raw_key}",
                        "X-DashScope-Async": "enable",
                        "Content-Type": "application/json",
                    }
                    resp = await client.post(url, headers=headers, json=payload)
                    if resp.status_code not in (200, 201, 202):
                        raise RuntimeError(f"DashScope HTTP {resp.status_code}: {resp.text[:200]}")
                    data = resp.json()
                    remote_id = data.get("output", {}).get("task_id") or ""
                    if not remote_id:
                        raise RuntimeError(f"Task ID tidak ditemukan pada respons DashScope: {resp.text[:200]}")

                elif provider_id == "video-siliconflow":
                    url = f"{base_url}/video/submit"
                    image_size = "1280x720" if aspect_ratio == "16:9" else ("720x1280" if aspect_ratio == "9:16" else "960x960")
                    payload = {
                        "model": model_name,
                        "prompt": enhanced,
                        "image_size": image_size,
                    }
                    if mode == "image_to_video" and image_url:
                        payload["image"] = image_url
                    headers = {
                        "Authorization": f"Bearer {raw_key}",
                        "Content-Type": "application/json",
                    }
                    resp = await client.post(url, headers=headers, json=payload)
                    if resp.status_code not in (200, 201, 202):
                        raise RuntimeError(f"SiliconFlow HTTP {resp.status_code}: {resp.text[:200]}")
                    data = resp.json()
                    remote_id = data.get("requestId") or data.get("id") or ""
                    if not remote_id:
                        raise RuntimeError(f"requestId tidak ditemukan pada respons SiliconFlow: {resp.text[:200]}")

                elif provider_id == "video-replicate":
                    url = f"{base_url}/models/{model_name}/predictions" if "/" in model_name else f"{base_url}/predictions"
                    input_payload: Dict[str, Any] = {"prompt": enhanced, "aspect_ratio": aspect_ratio}
                    if mode == "image_to_video" and image_url:
                        input_payload["first_frame_image"] = image_url
                    headers = {
                        "Authorization": f"Bearer {raw_key}",
                        "Content-Type": "application/json",
                    }
                    resp = await client.post(url, headers=headers, json={"input": input_payload})
                    if resp.status_code not in (200, 201, 202):
                        raise RuntimeError(f"Replicate HTTP {resp.status_code}: {resp.text[:200]}")
                    data = resp.json()
                    remote_id = data.get("id") or ""
                    out_val = data.get("output")
                    if isinstance(out_val, str) and out_val.startswith("http"):
                        video_url_out = out_val
                        status_val = "succeeded"
                        progress_val = 100
        except Exception as exc:
            status_val = "failed"
            progress_val = 0
            error_msg = str(exc)

        with db_session() as conn:
            conn.execute(
                """INSERT INTO video_jobs
                (id, provider_id, provider_name, model_name, mode, prompt, enhanced_prompt, image_url,
                 duration_sec, aspect_ratio, resolution, remote_job_id, status, progress_percent,
                 video_url, local_file_path, error_message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, ?, ?)""",
                (
                    job_id, provider_id, prov_name, model_name, mode, clean_prompt, enhanced,
                    image_url or "", duration_sec, aspect_ratio, resolution, remote_id,
                    status_val, progress_val, video_url_out, error_msg, now, now
                ),
            )
        return VideoGenerationManager.get_job(job_id)

    @staticmethod
    async def poll_video_job(job_id: str) -> Dict[str, Any]:
        job = VideoGenerationManager.get_job(job_id)
        if not job:
            raise ValueError("Video job tidak ditemukan.")
        if job["status"] in ("succeeded", "failed", "requires_api_key") or not job.get("remote_job_id"):
            return job

        provider_id = job["provider_id"]
        remote_id = job["remote_job_id"]
        raw_key, base_url = VideoGenerationManager._get_raw_key_and_url(provider_id)
        if not raw_key:
            return job

        new_status = job["status"]
        new_progress = min(90, int(job.get("progress_percent", 20)) + 15)
        video_url = job.get("video_url") or ""
        err_msg = job.get("error_message") or ""

        try:
            async with httpx.AsyncClient(timeout=20.0, follow_redirects=True) as client:
                if provider_id == "video-dashscope":
                    url = f"{base_url}/tasks/{remote_id}"
                    resp = await client.get(url, headers={"Authorization": f"Bearer {raw_key}"})
                    if resp.status_code == 200:
                        out = resp.json().get("output", {})
                        task_st = (out.get("task_status") or "").upper()
                        if task_st == "SUCCEEDED":
                            new_status = "succeeded"
                            new_progress = 100
                            video_url = out.get("video_url") or ""
                        elif task_st in ("FAILED", "CANCELED"):
                            new_status = "failed"
                            new_progress = 0
                            err_msg = out.get("message") or f"DashScope task {task_st}"
                elif provider_id == "video-siliconflow":
                    url = f"{base_url}/video/status"
                    resp = await client.post(
                        url,
                        headers={"Authorization": f"Bearer {raw_key}", "Content-Type": "application/json"},
                        json={"requestId": remote_id},
                    )
                    if resp.status_code == 200:
                        data = resp.json()
                        st = (data.get("status") or "").lower()
                        if st == "succeed":
                            new_status = "succeeded"
                            new_progress = 100
                            videos = data.get("results", {}).get("videos") or []
                            if videos:
                                video_url = videos[0].get("url") or ""
                        elif st in ("failed", "error"):
                            new_status = "failed"
                            new_progress = 0
                            err_msg = data.get("reason") or "SiliconFlow video rendering failed"
                elif provider_id == "video-replicate":
                    url = f"{base_url}/predictions/{remote_id}"
                    resp = await client.get(url, headers={"Authorization": f"Bearer {raw_key}"})
                    if resp.status_code == 200:
                        data = resp.json()
                        st = (data.get("status") or "").lower()
                        if st == "succeeded":
                            new_status = "succeeded"
                            new_progress = 100
                            out_val = data.get("output")
                            if isinstance(out_val, str):
                                video_url = out_val
                            elif isinstance(out_val, list) and out_val:
                                video_url = str(out_val[0])
                        elif st in ("failed", "canceled"):
                            new_status = "failed"
                            new_progress = 0
                            err_msg = str(data.get("error") or "Replicate job failed")
        except Exception as exc:
            err_msg = f"Polling warning: {exc}"

        with db_session() as conn:
            conn.execute(
                """UPDATE video_jobs
                SET status = ?, progress_percent = ?, video_url = ?, error_message = ?, updated_at = ?
                WHERE id = ?""",
                (new_status, new_progress, video_url, err_msg, _utc_now(), job_id),
            )
        return VideoGenerationManager.get_job(job_id)

    @staticmethod
    async def download_video_to_workspace(job_id: str) -> Dict[str, Any]:
        job = VideoGenerationManager.get_job(job_id)
        if not job:
            raise ValueError("Job video tidak ditemukan.")
        video_url = (job.get("video_url") or "").strip()
        if not video_url:
            raise ValueError("Video belum selesai dirender atau URL video belum tersedia.")

        target_dir = WORKSPACE_DIR / "downloads" / "videos"
        target_dir.mkdir(parents=True, exist_ok=True)
        target_file = target_dir / f"{job_id}.mp4"

        async with httpx.AsyncClient(timeout=60.0, follow_redirects=True) as client:
            resp = await client.get(video_url)
            if resp.status_code != 200:
                raise RuntimeError(f"Gagal mengunduh video (HTTP {resp.status_code})")
            target_file.write_bytes(resp.content)

        with db_session() as conn:
            conn.execute(
                "UPDATE video_jobs SET local_file_path = ?, updated_at = ? WHERE id = ?",
                (str(target_file), _utc_now(), job_id),
            )
        return VideoGenerationManager.get_job(job_id)
