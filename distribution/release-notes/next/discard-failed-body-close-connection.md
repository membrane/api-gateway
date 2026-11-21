---
type: fix
area: HTTP Transport and Runtime
---
- When the body of a request could not be read to its end, e.g. because the client stopped sending it, the client connection is now closed and the response says `Connection: close`, also when the flow replaced the request or its body, e.g. with a script, `template`, `setBody` or `call`. Before, the connection could be kept alive while the rest of the body was still on it.
