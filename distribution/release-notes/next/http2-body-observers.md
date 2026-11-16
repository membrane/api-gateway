---
type: fix
area: HTTP Transport and Runtime
---
- HTTP/2 message bodies that are streamed through the gateway are now recorded by exchange stores. Before, they were recorded as empty, because the body chunks of an HTTP/2 stream were not passed to the observers of the message.
