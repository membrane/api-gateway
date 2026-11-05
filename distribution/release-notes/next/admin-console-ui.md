---
type: feature
area: Admin Console
---
- `adminConsole`: new web UI under `/console/`, built on the admin REST API v1. It shows the recorded calls of all instances with live updates and filters, the details of a call (headers, pretty-printed body, curl command), the APIs and system information. It follows the light or dark mode of the browser. `/` now redirects to `/console/`; the previous UI stays available under `/admin` for now
