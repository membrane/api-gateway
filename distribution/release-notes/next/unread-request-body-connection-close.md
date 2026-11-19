---
type: fix
area: HTTP Transport and Runtime
---
- A response to a request whose body was not read completely, e.g. because a plugin rejected the request, now carries `Connection: close`. Membrane already closed such a connection after the response; now the client knows before it sends the next request
