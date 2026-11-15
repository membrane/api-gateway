---
type: security
area: HTTP Transport and Runtime
---
- Chunked message bodies are now parsed strictly as RFC 9112 defines them. Every chunk-size line, chunk, trailer line and the end of the body must end with CRLF, and a chunk size must consist of hexadecimal digits only. Before, Membrane accepted any two bytes after a chunk, a bare LF after a chunk extension or in the trailer, a CR followed by any byte at the end of the trailer, a sign or whitespace around the chunk size, and a body that ended in the middle of its last line. Such a body is now rejected as unreadable instead of being forwarded.
