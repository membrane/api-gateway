---
type: breaking
area: HTTP Transport and Runtime
---
- A message with a transfer coding other than `chunked`, e.g. `Transfer-Encoding: gzip, chunked`, is now rejected: a request with `501 Not Implemented`, a backend response with `502 Bad Gateway`. Membrane does not decode such codings. Before, they were forwarded unchanged, so plugins (validators, `template`, expressions, scripts) worked on the compressed bytes, and an HTTP/2 client got them as content without any error. A request that applies `chunked` more than once is rejected with `400`. [#3789](https://github.com/membrane/api-gateway/issues/3789)
- A backend response with invalid framing (e.g. conflicting `Content-Length`) or an unsupported transfer coding is no longer retried on the same node. An idempotent request is still sent to another node, if there is one. [#3789](https://github.com/membrane/api-gateway/issues/3789)
