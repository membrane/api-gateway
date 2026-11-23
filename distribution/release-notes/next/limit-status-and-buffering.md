---
type: breaking
area: Plugins
---
- `limit`:
  - A request over the limit is answered with `413 Content Too Large`, a response over the limit is replaced by `502 Bad Gateway`. Before, both were answered with `500` [#3689](https://github.com/membrane/api-gateway/issues/3689)
  - A body up to `maxBodyLength` is held in memory while it passes. A body of known length without `Content-Encoding` is still passed on as it arrives. Streamed responses, such as server-sent events, are held back until they end; place `limit` in the request flow to limit only requests
  - A body compressed with gzip, deflate or br must stay within `maxBodyLength` both as received and once decoded. A body with another `Content-Encoding` is limited by the bytes received [#3777](https://github.com/membrane/api-gateway/issues/3777)
