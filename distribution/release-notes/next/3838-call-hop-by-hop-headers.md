---
type: fix
area: Plugins
---
- `call`: the hop-by-hop fields of the endpoint's response (`Connection` and the fields it names, `Keep-Alive`, `Proxy-Connection`, `TE`, `Trailer`, `Upgrade`) are no longer copied onto the message. Before, e.g. a `Connection: close` from the endpoint closed the client connection, and an `Upgrade` offer from the endpoint made the request to the target fail with `403`.
- `call`: the same fields of the message are no longer sent to the endpoint. Before, e.g. a client's `Connection: Upgrade` with `Upgrade: websocket` made the call fail with `500`.
- `call`: when the endpoint answers with a compressed body, the message's `Content-Length` now matches the decoded body. Before, the endpoint's `Content-Length` of the compressed body was copied onto the message.
- `call`: with `POST`, `PUT` or `PATCH`, a compressed message body is sent to the endpoint decoded. Before, the compressed bytes were sent without their `Content-Encoding`.
