---
type: feature
area: Plugins
---
- `rateLimiter`: the request counts can be kept in Redis with the new `redisRateLimitStore`, so several Membrane instances share one limit instead of each counting on its own. It uses a `redis` component as connection. The new attribute `failOpen` (default `true`) decides whether requests pass or get 503 when the store cannot be reached.
