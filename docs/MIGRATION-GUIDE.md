# Migration from 5.X to 6

## Swagger 2

The `<swagger>` proxy is no longer supported. The new `api` proxy also supports Swagger version 2.

## Scripting

Spring Expression Language (SpEL) is now the default for `<setHeader>`, `<if>`, `<setProperty>`. ...

## setHeader

If there is no message to set a header, now an exception is thrown.

## InternalProxy

`<internalProxy>` is renamed to `<internal>`. To reference an internal proxy use the protocol `internal://name`.

Instead of:

```xml
<target url="service:a"/>
```

use:

```xml
<target url="internal://a"/>
```

See: examples/routing-traffic/internalproxy

## xPAth and xPathExtractor
Both plugins were removed. Use `<setHeader>` or `<setProperty>` with language `xpath` instead.

## Content Based Router

The content based router has been removed. Use `<if>` and `<destination>` instead.

See: examples/routing-traffic/content-based-router

## Log

Instead of `headerOnly="true"` use `body="false"` in the `<log>` plugin.

## Groovy

Use `$property.foo` instead of `$foo`. Fields are not accessible just by name.

## OAuth2Resource removal

Rename `<oauth2Resource>` to `<oauth2Resource2>`.

Remove the `publicURL` attribute from it: It will be automatically computed from the incoming `Host` header.

## Error Messages

Almost all error messages are now in ProblemJson format.

## Monitoring

Default naming scheme for `<serviceProxys>` has changed. This might affect existing filters in log aggregation systems and/or monitoring dashboards, e.g. in Prometheus/Grafana.")

## Internal API

- `Interceptor.handleRquest()` and `handleRquest()` aren't throwing any exception anymore
- `ConditionalInterceptor` is renamed in `IfInterceptor`
