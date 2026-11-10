---
type: feature
area: WSDL and SOAP
---
- New `soap2Json` plugin: converts the Body of SOAP requests and responses (SOAP 1.1 and 1.2) to JSON, using the WSDL for the mapping. Numbers and booleans come out typed, repeatable elements are always arrays, and `xsi:nil` becomes `null`, the same mapping `wsdl2openapi` uses. A SOAP Fault, a message the WSDL does not declare, or a body that is not XML is answered with a problem details document. Only document/literal messages are supported.
