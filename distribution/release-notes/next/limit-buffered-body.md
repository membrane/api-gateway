---
type: fix
area: Plugins
---
- `limit` now reads a body into memory, up to `maxBodyLength`, before it passes it on, so a message over the limit is rejected before any part of it is forwarded:
  - Chunked bodies are forwarded with their content. Before, they arrived empty or truncated, and a chunked body over the limit was not rejected [#3722](https://github.com/membrane/api-gateway/issues/3722)
  - Messages are forwarded with their header fields as received. Compressed bodies are forwarded compressed
  - Responses to `HEAD` requests and `304` responses are no longer rejected because of their `Content-Length` [#3690](https://github.com/membrane/api-gateway/issues/3690)
  - XOP/MTOM packages are forwarded unchanged. Before, they were forwarded as reassembled XML with the multipart `Content-Type` [#3691](https://github.com/membrane/api-gateway/issues/3691)
  - The rest of a request body over the limit is not read any more; the connection is closed after the error response
- A body with a `Content-Length` that is read into memory, by `limit` or any other plugin, takes up memory as its bytes arrive, not for its whole length at once
