---
type: improvement
area: Plugins
---
- `rateLimiter`: successful responses now carry the quota headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` and `X-RateLimit-Duration`, not only rejected ones. When several limiters apply to a request, the headers describe the one with the fewest requests left. `X-RateLimit-Reset` is computed when the response is sent, so it accounts for the time the backend took.
- New tutorials in `tutorials/rate-limiting`: limits per client IP, a global limit for all APIs, limits per JSON field and tiered limits per API key.
