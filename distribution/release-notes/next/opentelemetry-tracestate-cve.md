---
type: security
area: Plugins
---
- `openTelemetry`: updated OpenTelemetry to 1.67.0, which fixes CVE-2026-118259. A crafted `tracestate` request header made the parsing of the incoming trace context take quadratic CPU time.
