---
type: fix
area: HTTP Transport and Runtime
---
- `X-Forwarded-Host` and `X-Forwarded-Proto` received in several field lines are now kept completely when the gateway rewrites them into one line (RFC 9110 §5.3). Before, only the first line was kept, so values a proxy added as a separate line were not forwarded to the backend.
