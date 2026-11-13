---
type: fix
area: Plugins
---
- `balancerHealthMonitor`: health checks now run every `interval` milliseconds. Before, they always ran every 5 seconds, whatever `interval` was set to. The first check runs one interval after startup. Configurations without `interval` now check every 10 seconds, the documented default.
