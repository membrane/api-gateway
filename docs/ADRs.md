# ADR Discussions

## Make flow a property of exchange 
  - That would support a method like getMessage
  - Flow is more a property of E

## Type of setProperty

- Should it always be String or should it be possible to set other objects?

Agreed!

### Considerations
- Object makes sense to cover more complex use cases
- String makes it easier

### Decision

## proxies.xml

- Take out ASF License

## Logging of data

Should we log data in case of error?

## Exception for Returning Detailed Error Messages

e.g. setHeader should return in not production mode details about the error. But setHeader should not deal with deciding to use XML or JSON for the response.

# Naming

- ..ExampleTest

## More

- WADL remove?

## Internal Proxy?

- <api name=""/> opens port 80 / Do we need internal 

# setHeader returns null

 <setHeader value="headers['unknown'] ...> should set null.

 - Makes handling less easy if it is null
 
# Use double brace initialization?

{{}}

See: https://stackoverflow.com/questions/1958636/what-is-double-brace-initialization-in-java

# Initialization

Init first proxies and than their interceptors

Should implementations of init() in subclasses of Interceptor call init() on super? 
- Otherwise so initialization is missing out.