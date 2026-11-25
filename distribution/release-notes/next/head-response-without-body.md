---
type: fix
area: HTTP Transport and Runtime
---
- A response to a `HEAD` request over HTTP/1.1 now ends after its header fields. Before, a body that a plugin such as `template` or `return` had set was sent after them, and a backend response with `Transfer-Encoding: chunked` got a closing chunk.
