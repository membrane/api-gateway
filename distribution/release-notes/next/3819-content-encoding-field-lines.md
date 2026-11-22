---
type: fix
area: HTTP Transport and Runtime
---
- `Content-Encoding` is now read from all its header lines (RFC 9110 §5.3). Before, only the first line counted. A body sent with `Content-Encoding: gzip` and a second line `Content-Encoding: br` was run through the gzip decoder and failed with 400 for a request or 500 for a response. Several codings are now treated like a coding Membrane does not decode, and the body is forwarded unchanged. Empty list elements are ignored, so `Content-Encoding: , gzip,` is decoded as `gzip`. `Header.getContentEncoding()`, which reads only the first line, is deprecated and will be removed in 8.0; scripts should use `getContentCodings()` [#3819](https://github.com/membrane/api-gateway/issues/3819)
