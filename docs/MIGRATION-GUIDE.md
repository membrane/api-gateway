# Migration from 5.X to 6

# Swagger 2

The old `<swagger>` proxy is no longer supported. The new `api` proxy also supports Swagger version 2.
See example...

## InternalProxy

<internalProxy name="foo"/>  -> <internal name="foo"></internal>

<target url="service:a"/> -> <target url="internal://a"/>

# Scripting

SpeL default für setHeader, if, setProperty

# setHeader

Now if there is no message to set a header, an exception is thrown.

# Internal API

- ConditionalInterceptor is renamed in IfInterceptor

# <xPAth> and <xPathExtractor>
- Use `<setHeader>` or `<setProperty>` with language `xpath` instead.

# CBR

# Log

- Instead of headerOnly="true" body="false"

# Internal API

- exchange.destinations can now be set exchange.setDestination() with an immutable list
  => no getDestinations().clear() anymore

# Scripting 

## Groovy

Use $property.foo instead of $foo. Fields are not accessible just by name!