# 6.0.0

Membrane Version 6 is a big step forward from Membrane 5. Big parts of the code base were refactored and improved. In addition to that lots of new features have been added.

## New Features
- setHeader now supports also Groovy, XPath, Jsonpath
- New plugins `call`, `destination`
- API key stores for JDBC and MongoDB

## Improvements
- ProblemDetails format is used for error messages
- Ordered fields in ProblemDetails
- New flow control. Not based on a stack of executed interceptors but on the definition in the apis.xml.
- New `log` plugin with more features and cleaner configuration. It can now dump the exchange and properties
- References in OpenAPI are now supported
- In SpEL and Groovy plugins besides `headers.` and `properties.` now also the singulars `header.` will work
