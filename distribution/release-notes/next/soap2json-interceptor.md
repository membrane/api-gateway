---
type: feature
area: WSDL and SOAP
---
- New `soap2Json` plugin: converts the Body of SOAP requests and responses (SOAP 1.1 and 1.2) to JSON, using the WSDL for the mapping. Numbers and booleans come out typed, repeatable elements are always arrays, and `xsi:nil` becomes `null`, the same mapping `wsdl2openapi` uses. A SOAP Fault, a message the WSDL does not declare, or a body that is not XML is answered with a problem details document. Only document/literal messages are supported.
- New `json2Soap` plugin: converts a JSON body to the SOAP request or response of a WSDL operation, with the elements in schema order and the SOAPAction or SOAP 1.2 action the binding declares. The operation is configured, or taken from the exchange property `membrane.soap.operation` that `soap2Json` sets for a request, so the two together put a SOAP interface in front of a JSON backend. An error status from the backend becomes a SOAP Fault.
