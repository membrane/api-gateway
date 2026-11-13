---
type: fix
area: Plugins
---
- `limit`: a body with a `Content-Encoding` other than gzip, deflate or br, e.g. `zstd`, is no longer forwarded without its `Content-Encoding` header. Such a body is not decoded, so the header is kept and the limit applies to the encoded bytes [#3778](https://github.com/membrane/api-gateway/issues/3778)
