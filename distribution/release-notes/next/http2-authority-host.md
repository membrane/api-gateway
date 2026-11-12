---
type: fix
area: HTTP Transport and Runtime
---
- HTTP/2: a request whose `host` field differs from `:authority`, or that has several different `host` fields, is rejected as malformed. A `host` that repeats `:authority` is accepted, and the request is forwarded with one `Host` field.
