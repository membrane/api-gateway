---
type: security
area: Plugins
---
- `json2Xml` and `xml2Json`: updated org.json to 20260814, which fixes CVE-2026-59171. A number with hundreds of thousands of digits in a request or response body kept the thread busy for seconds while it was parsed.
