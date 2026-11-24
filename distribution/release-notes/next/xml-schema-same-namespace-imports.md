---
type: fix
area: WSDL and SOAP
---
- `validator` with `wsdl` or `schema`: a schema that imports one namespace from several documents (for example request types and response types in separate XSD files) now validates against all of them. Before, only the first document of each namespace was loaded, so elements declared in the others were rejected with `cvc-elt.1.a: Cannot find the declaration of element`. A declaration that several of these documents repeat, such as a local copy of a shared schema, is accepted, and the first one wins. A missing document is now a configuration error for every import of a namespace, as it already was for the first.
