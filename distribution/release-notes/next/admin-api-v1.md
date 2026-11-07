---
type: feature
area: Admin Console
---
- `adminConsole`: new REST API under `/admin/api/v1`, described by an OpenAPI document (`admin-api-v1.yaml`). It lists APIs of all types (`api`, `serviceProxy`, `soapProxy`, `proxy`, `internal`, ...) with their matching rules, target and nested flow, and the recorded calls with filters for time range, status class (e.g. `4xx`), headers and body. For a single call it offers the headers, the raw message, a pretty-printed body (JSON, XML, YAML, form data), the body as a download and a curl command line. It also covers statistics, stream pumps, JVM information and load balancers. Changes need a same-origin request, and a `readOnly` console rejects them, except the node actions `up`, `down`, `takeout` and `reset` of load balancers. Endpoints that are planned but not available yet answer `501`
- `adminConsole`: new attribute `instanceName` names the instance in the admin API; it defaults to the host name
