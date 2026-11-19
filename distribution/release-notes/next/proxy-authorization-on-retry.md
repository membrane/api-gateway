---
type: fix
area: HTTP Transport and Runtime
---
- The client's own `Proxy-Authorization` header is now restored after a plain HTTP attempt through an outbound proxy with `authentication`. Before, a retry to an HTTPS target after a failed plain HTTP attempt sent the proxy credentials through the tunnel to the backend, and after a call through the proxy they stayed on the request. Whether a plain HTTP proxy removes the credentials before forwarding is still up to the proxy.
