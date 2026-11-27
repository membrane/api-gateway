---
type: fix
area: Plugins
---
- `greaser`, and `groovy` or `javascript` scripts that return the unchanged `request` or `response`, no longer drop the message body. Before, the body was discarded while its `Content-Length` stayed, so the backend or the client waited for a body that never came, e.g. when `greaser` left a non-JSON message unchanged.
