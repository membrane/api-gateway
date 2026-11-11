---
type: security
area: HTTP Transport and Runtime
---
- Messages with `Content-Encoding: deflate` are now decompressed while they are read, like gzip and Brotli. Before, the whole body was decompressed into memory first, so a reader that stops early, such as `limit` with `maxBodyLength`, could not stop a small compressed body that expands to a very large one
