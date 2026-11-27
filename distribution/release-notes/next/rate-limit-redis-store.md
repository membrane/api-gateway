---
type: feature
area: Plugins
---
- `rateLimiter`: the request counts can be kept in Redis with the new `redisRateLimitStore`, so several Membrane instances share one limit instead of each counting on its own. Instances share counts only if their limiters use the same `prefix` and `requestLimitDuration`. A different `prefix` keeps separate counts. It uses a `redis` component as connection. The new attribute `failOpen` (default `true`) decides whether requests pass or get 503 when the store cannot be reached.
