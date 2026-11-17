---
type: fix
area: HTTP Transport and Runtime
---
- The credentials of an outbound proxy with `authentication` no longer reach the backend. Before, a retry to an HTTPS target after a failed plain HTTP attempt sent them through the tunnel to the backend, and after a call through the proxy they stayed on the request. The client's own `Proxy-Authorization` header is now restored in both cases
