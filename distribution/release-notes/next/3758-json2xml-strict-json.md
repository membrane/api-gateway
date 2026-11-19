---
type: breaking
area: Plugins
---
- `json2Xml`: the body must be exactly one standard JSON value. Before, without `xmlSchema`, input after the first value was silently dropped (`{"a":1} {"a":2}` became `<a>1</a>`), and trailing commas, unquoted keys, single quotes and plain text were accepted. Such a body is now rejected with `400` in a request and `500` in a response. Clients that send non-standard JSON have to send valid JSON.
- `json2Xml`: an empty body with a JSON Content-Type is rejected the same way, with or without `xmlSchema`. Before, it became `<root></root>`, or passed through unchanged with `xmlSchema`. A `204`, `304` or other response that has no body by HTTP, and the response to a `HEAD` request, still pass through unchanged.
