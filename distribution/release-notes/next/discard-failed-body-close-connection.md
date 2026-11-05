---
type: fix
area: HTTP Transport and Runtime
---
- When the body of a message could not be read to its end, e.g. because the client stopped sending it, the message is now marked `Connection: close`, so the connection is not reused while the rest of the body is still on it. Before, an error response that replaced the failed body could let the connection be kept alive
