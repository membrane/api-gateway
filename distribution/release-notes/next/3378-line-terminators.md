---
type: fix
area: HTTP Transport and Runtime
---
- Line endings in request lines, status lines and header lines follow RFC 9112: a line ends with CRLF or a bare LF, and a CR that is not followed by LF is rejected, with `400 Bad Request` for a request and `502 Bad Gateway` for a backend response. Before, the byte after a CR was dropped, and an LF followed by CR was read as one line ending.
- A request whose header section ends with a bare LF is now answered right away. Before, Membrane waited for one more byte from the client.
