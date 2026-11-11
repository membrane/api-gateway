---
type: fix
area: HTTP Transport and Runtime
---
- Empty lines before the request line are now ignored, as RFC 9112 recommends, e.g. a CRLF that a client sends after a request body without counting it in `Content-Length`. Before, Membrane closed the keep-alive connection without a response, so the next request on that connection failed. Up to 10 empty lines are ignored.
