---
type: fix
area: WSDL and SOAP
---
- XOP/MTOM: a multipart message that cannot be reassembled, e.g. because an `xop:Include` references a missing part or has no `href`, the start part is not well-formed XML, or the message is truncated, is now answered with `400` for a client request and logged at `info`. Before, the client got a `500` and the failure was logged as an error with a stack trace
