---
type: security
area: WSDL and SOAP
---
- XOP/MTOM: a multipart message in which several `xop:Include` elements reference the same part is now rejected, for a client request with `400`. Before, the part was inserted again for every reference, so a small message could expand to a very large one. SOAP 1.2 MTOM does not allow a part to be referenced more than once
