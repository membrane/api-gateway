# 6.0.0

## New Features
- setHeader now supports also Groovy, XPath, Jsonpath


## Improvements
- Ordered fields in ProblemDetails
- Example Tests without unzipping for every test
- New flow control. Not based on a stack of executed interceptors but on the definition in the apis.xml.

## Scripting Languages

- In SpEL and Groovy besides headers. and properties. now also the singulars header. will work

## Swagger 2

- SwaggerInterceptor is removed
  - Use new openapi instead