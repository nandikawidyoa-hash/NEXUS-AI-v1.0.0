import asyncio
import json
from typing import Any, Dict, List
from fastapi import WebSocket

class WebSocketHub:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, ws: WebSocket):
        await ws.accept()
        self.active_connections.append(ws)

    def disconnect(self, ws: WebSocket):
        if ws in self.active_connections:
            self.active_connections.remove(ws)

    async def broadcast(self, event_type: str, payload: Dict[str, Any]):
        msg = json.dumps({"event": event_type, "data": payload})
        stale = []
        for ws in list(self.active_connections):
            try:
                await ws.send_text(msg)
            except Exception:
                stale.append(ws)
        for s in stale:
            self.disconnect(s)

ws_hub = WebSocketHub()
