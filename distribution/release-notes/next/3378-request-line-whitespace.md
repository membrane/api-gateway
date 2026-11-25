---
type: breaking
area: HTTP Transport and Runtime
---
- The parts of a request line must be separated by exactly one space, and the line must not contain any other whitespace, e.g. two spaces, a tab, or a space at the end. Such a line is answered with `400 Bad Request`. Before, a second space before the HTTP version or a space at the end of the line was passed on to the backend unchanged. Clients have to send single spaces.
