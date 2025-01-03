# Migration from 5.X to 6

# Swagger 2

The old `<swagger>` proxy is no longer supported. The new `api` proxy also supports Swagger version 2.
See example...

## InternalProxy

<internalProxy name="foo"/>  -> <api name="foo"></api>

<target url="service:a"/> -> <target url="service://a"/>

# Scripting

SpeL default für setHeader, if

# setHeader

Now if there is no message to set a header, an exception is thrown.

# Internal API

- ConditionalInterceptor is renamed in IfInterceptor

# Log

- Instead of headerOnly="true" body="false"