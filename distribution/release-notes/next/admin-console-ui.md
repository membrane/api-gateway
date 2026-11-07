---
type: feature
area: Admin Console
---
- `adminConsole`: new web UI under `/console/`, built on the admin REST API v1. It shows the recorded calls of all instances with live updates and filters, the details of a call (headers, pretty-printed body, curl command), the APIs and system information. It follows the light or dark mode of the browser. If the admin API requires an `apiKey`, the console asks for it and keeps it for the browser tab. The UI is packaged in the module `service-proxy-admin-ui`, which the distribution contains. With it on the classpath, `/` redirects to `/console/`; without it, `/console/` is not served and `/` redirects to `/admin` as before. The previous UI stays available under `/admin` for now
- Admin console calls: more filters (time range, minimum duration, API, client, header, body text), a column chooser and saved filters (kept in the browser), export of selected calls as HAR for the browser's developer tools, comparison of two calls (header fields and a diff of the formatted bodies, also across instances), and replay of a recorded request, changed if needed, through the gateway or straight to the backend. Replay needs a console that is not `readOnly`
