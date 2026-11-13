---
type: fix
area: Plugins
---
- `limit`: a body with a `Content-Encoding` that Membrane does not decode is no longer forwarded without its `Content-Encoding` header. Such a body is not decoded, so the header is kept and the limit applies to the encoded bytes [#3778](https://github.com/membrane/api-gateway/issues/3778)
