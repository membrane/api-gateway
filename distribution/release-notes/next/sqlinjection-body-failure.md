---
type: fix
area: Plugins
---
- `sqlInjectionProtection`: a request body that cannot be read, e.g. because the client stopped sending it, is now answered with `400` and logged at `info`, as in other plugins. A response body that cannot be read, e.g. because the backend broke the connection, is answered with an internal-error response (`500`). Before, both were answered with a `500` `Error inspecting REQUEST!`/`RESPONSE!` and logged as an error
