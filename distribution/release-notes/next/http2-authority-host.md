---
type: fix
area: HTTP Transport and Runtime
---
- HTTP/2 (TLS listeners with `useExperimentalHttp2`): a request whose `host` field differs from `:authority`, or that has several different `host` fields, is rejected as malformed. A `host` that repeats `:authority`, apart from case and the scheme's default port, is accepted, and the request is forwarded with one `Host` field.
