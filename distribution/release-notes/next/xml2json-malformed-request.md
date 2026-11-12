---
type: fix
area: Plugins
---
- `xml2Json`: malformed XML in a request is answered as a client error that says the XML could not be converted, instead of a 400 titled "Internal server error." with the details hidden in production.
