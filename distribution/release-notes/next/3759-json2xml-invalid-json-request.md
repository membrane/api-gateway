---
type: fix
area: Plugins
---
- `json2Xml`: invalid JSON in a request is answered with `400` instead of `500`, with a detail saying the JSON could not be converted to XML. Invalid JSON from the backend is still a `500`. The parser's message is no longer shown to the client in production, and the body is no longer logged.
