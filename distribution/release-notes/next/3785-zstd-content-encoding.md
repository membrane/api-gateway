---
type: feature
area: HTTP Transport and Runtime
---
- Bodies with `Content-Encoding: zstd` (RFC 9659) are now decoded like gzip, deflate and br, so plugins that read the body, such as validators, `template`, JSONPath/XPath expressions and scripts, get the content instead of the compressed bytes, and `limit` counts the decoded bytes. Browsers offer zstd in `Accept-Encoding`, so backends increasingly answer with it. A zstd frame that needs a window larger than 8 MB is rejected, following the limit RFC 9659 sets for this coding, with 400 for a request and 500 for a response [#3785](https://github.com/membrane/api-gateway/issues/3785)
