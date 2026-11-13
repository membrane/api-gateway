---
type: fix
area: Plugins
---
- `redisSessionManager`: reading a session now extends its lifetime in Redis by `expiresAfterSeconds`. Before, every read reset it to the `timeout` of the `redis` connector, a value in milliseconds that was taken as seconds: 5000 seconds (about 83 minutes) by default. (#3779)
