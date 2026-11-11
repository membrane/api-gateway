---
type: fix
area: HTTP Transport and Runtime
---
- A request with a `Content-Type` header that cannot be parsed, e.g. `application/json; charset`, is now answered with `400` and logged at `info` when a plugin reads its body. The problem type `.../problems/user/content-type` and the detail name the header, also in production. Before, the client got a `500` `Internal server error.`, and the gateway logged an error with a stack trace for every such request
- XOP/MTOM: a request whose package cannot be reassembled, e.g. because a referenced part is missing or the package is truncated, is now answered with the problem type `.../problems/user/xop`, and the reason is also shown in production
- In production, a request body that cannot be read, e.g. a broken gzip body, is now logged at `info` with a log key that the problem details response also contains, including in `openapi`. The `validator` no longer logs a stack trace for it
