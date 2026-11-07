---
type: fix
area: HTTP Transport and Runtime
---
- A request whose body cannot be read while it is sent to the backend, e.g. because the client stopped sending it, is now answered with `400` and logged at `info`, as in other plugins. Before, the client got a `500` `Error sending request to backend.`, logged as an error. Such a request is also no longer retried, as its body cannot be sent again
