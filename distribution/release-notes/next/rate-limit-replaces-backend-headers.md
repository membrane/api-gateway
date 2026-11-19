---
type: breaking
area: Plugins
---
- `rateLimiter`: on responses to requests that passed a `rateLimiter`, the headers `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` and `X-RateLimit-Duration` now describe the limiter's quota. Headers of the same name sent by the backend are replaced. Before, they reached the client unchanged.
