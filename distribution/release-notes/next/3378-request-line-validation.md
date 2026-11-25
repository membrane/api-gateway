---
type: fix
area: HTTP Transport and Runtime
---
- Request lines are checked against RFC 9112: `method SP request-target SP HTTP-version`, where the method is a token and the version is `HTTP/` followed by digit `.` digit. Any other line is answered with `400 Bad Request`. Before, such a line was routed with a misread method, target or version, answered with `501 Not Implemented`, or the connection was closed without a response.
- A request with a major HTTP version other than 1, e.g. `HTTP/2.0` in an HTTP/1 request line, is answered with `505 HTTP Version Not Supported`. HTTP/2 over TLS is not affected.
- A first line is read as a STOMP frame only if it is `CONNECT` or `STOMP`, the frames that open a `stompProxy` session.
- `stompProxy`: a `STOMP` frame is no longer rejected with `501 Not Implemented` when `knownMethodValidator` is configured. STOMP commands are not checked by the method validator.
