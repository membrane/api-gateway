# 7.3.0

## New Features
- Add configurable HTTP method validation via a `methodValidator` component, with `defaultMethodValidator`, `knownMethodValidator`, `rfc9110MethodValidator` and `uppercaseMethodValidator` policies; requests with a disallowed method are rejected with `501 Not Implemented` before reaching the flow [#3032](https://github.com/membrane/api-gateway/pull/3032)

# 6.0.0

Membrane Version 6 is a big step forward from Membrane 5. Big parts of the code base were refactored and improved.

## New Features
- setHeader now supports also Groovy, XPath, Jsonpath
- New plugins `call`, `destination`
- API key stores for JDBC and MongoDB

## Improvements
- New flow control through plugins. Not based on a stack of executed interceptors but on the definition in the 'proxies.xml' file.
- New `log` plugin with more features and cleaner configuration. It can now dump the exchange and properties
- ProblemDetails format is used for most of the error messages
- Ordered fields in ProblemDetails
- References in OpenAPI are now supported
- In SpEL and Groovy plugins besides `headers.` and `properties.` now also the singulars `header.` will work
