---
type: fix
area: HTTP Transport and Runtime
---
- A body with `Content-Encoding: x-gzip` is now decoded like `gzip` (RFC 9110 §8.4.1.3). Before, plugins that read the body, such as validators, `template`, JSONPath/XPath expressions and scripts, got the compressed bytes, and `limit` counted the compressed length.
