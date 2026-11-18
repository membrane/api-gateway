---
type: fix
area: HTTP Transport and Runtime
---
- `X-Forwarded-For` received in several field lines is now kept completely when the gateway appends the address of the connection (RFC 9110 §5.3). Before, only the first line was kept, so entries a proxy added as a separate line were not forwarded to the backend or seen by plugins such as `rateLimiter`.
