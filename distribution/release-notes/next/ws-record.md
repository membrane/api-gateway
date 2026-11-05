---
type: feature
area: WebSockets
---
- New frame plugin `wsRecord` for the flow of `webSocket`: records the messages of recent WebSocket connections in memory, so the admin console shows them on the call that opened the connection. `maxMessages`, `maxPayloadBytes` and `maxConnections` limit what is kept; messages are passed on unchanged
