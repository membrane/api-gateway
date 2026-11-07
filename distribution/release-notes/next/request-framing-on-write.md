---
type: fix
area: HTTP Transport and Runtime
---
- A request with a body but without `Content-Length` and `Transfer-Encoding` is now sent to the backend with framing: chunked while the body is streamed, or with `Content-Length` if its length is known. Before, the body was sent without either, so the backend could read it as a further request. This happened when a `headerFilter` removed these fields, as in the WAR's shipped configuration, and for HTTP/2 requests without `content-length`.
