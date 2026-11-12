---
type: fix
area: Plugins
---
- `stompProxy`: a STOMP session can also be opened with a `STOMP` frame, as STOMP 1.2 requires. Before, it was answered with `400`. [#3768](https://github.com/membrane/api-gateway/pull/3768)
- `stompProxy` and `stompClient`: the client no longer gets an HTTP `500` before the broker's `CONNECTED` frame. This broke STOMP sessions since 6.0.0. [#3768](https://github.com/membrane/api-gateway/pull/3768)
