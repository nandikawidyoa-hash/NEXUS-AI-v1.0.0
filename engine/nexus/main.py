import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, status
from fastapi.middleware.cors import CORSMiddleware

from nexus import __app_name__, __version__
from nexus.api.routes import router
from nexus.api.ws import ws_hub
from nexus.config import HOST, PORT
from nexus.database.db import init_db
from nexus.security.auth import verify_token_string
from nexus.skills.manager import SkillManager

def create_app() -> FastAPI:
    init_db()
    SkillManager.sync_filesystem_skills()

    app = FastAPI(
        title=__app_name__,
        version=__version__,
        description="NEXUS AI Hybrid Local-Cloud Personal Agent Engine for Android",
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(router)

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket):
        token = (
            websocket.headers.get("authorization")
            or websocket.headers.get("x-nexus-token")
            or websocket.query_params.get("token")
        )
        if not verify_token_string(token):
            await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
            return

        await ws_hub.connect(websocket)
        try:
            await websocket.send_json({
                "event": "connected",
                "data": {"service": __app_name__, "version": __version__},
            })
            while True:
                msg = await websocket.receive_text()
                await websocket.send_json({"event": "pong", "data": {"echo": msg}})
        except WebSocketDisconnect:
            ws_hub.disconnect(websocket)
        except Exception:
            ws_hub.disconnect(websocket)

    return app

app = create_app()

if __name__ == "__main__":
    uvicorn.run("nexus.main:app", host=HOST, port=PORT, reload=False)
