---
type: improvement
area: HTTP Transport and Runtime
---
- POST and PATCH request bodies are now streamed to the backend over HTTP/1, also when retries are enabled, which is the default. Before, Membrane read the whole body into memory before sending the first byte, so that it could resend the request. Such requests are only retried when the connection to the backend could not be established, which happens before any part of the body has been sent. GET, PUT, DELETE and other idempotent requests are still kept in memory for retries.
