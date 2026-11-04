---
type: fix
area: Plugins
---
- `sqlInjectionProtection`: a body that cannot be read, e.g. because the client stopped sending it, is now answered with `400` and logged at `info`, as in other plugins. Before, it was answered with a `500` `Error inspecting REQUEST!` and logged as an error
