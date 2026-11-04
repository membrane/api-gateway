---
type: fix
area: Plugins
---
- `limit` with `maxBodyLength` forwarded a chunked message (`Transfer-Encoding: chunked`) with an empty body, or failed it if it was compressed, e.g. with gzip. A compressed message with a `Content-Length` was forwarded decompressed without any length, so the receiver could not tell where the body ended. Such messages are now forwarded decompressed and chunked, and measured while they are forwarded. When one turns out to be larger than `maxBodyLength`, the transfer is cut off and the connection is closed: the backend may already have received part of a request, and a response reaches the client truncated. An uncompressed body with a `Content-Length` within the limit is passed on unchanged
- A body that a plugin after `limit` reads and finds larger than `maxBodyLength` is answered with `Message is too large.`, unless that plugin reports the failure itself
