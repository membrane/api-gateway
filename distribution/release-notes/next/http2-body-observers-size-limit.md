---
type: breaking
area: HTTP Transport and Runtime
---
- Exchange stores with `bodyExceedingMaxSizeStrategy: ERROR` now apply `maxBodySize` to HTTP/2 message bodies too, as they already did for HTTP/1. An HTTP/2 message whose body is larger than the limit now fails instead of passing through unchecked.
