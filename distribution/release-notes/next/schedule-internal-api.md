---
type: feature
---
- `schedule`: an `internal` API can now be called periodically, e.g. to refresh a token, warm up a cache or send a heartbeat. `interval` sets the time between two calls as an ISO 8601 duration like `PT10M`. Each call runs through the flow and the target of the API like a request that came in over HTTP. Calls never overlap. Every Membrane instance runs its own schedules. See the tutorials `orchestration/60-Scheduler.yaml` and `70-Schedule-Call.yaml`
