---
type: fix
area: HTTP Transport and Runtime
---
- TLS: a ClientHello with an invalid SNI host name, e.g. one with a trailing dot like `example.com.`, or a first handshake message that is not a ClientHello, now closes the connection quietly like other malformed handshakes, and is logged only when `showSSLExceptions` is enabled. Before, Membrane logged an error with a stack trace for every such connection.
