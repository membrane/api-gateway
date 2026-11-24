---
type: fix
area: WSDL and SOAP
---
- `validator` with `wsdl` or `schema`: a schema that imports one namespace from several documents (for example request types and response types in separate XSD files) now validates against all of them. Before, only the first document of each namespace was loaded, so elements declared in the others were rejected with `cvc-elt.1.a: Cannot find the declaration of element`. Schema sets that only compile when the later imports are ignored, because they import duplicate declarations of the same namespace, still work as before.
