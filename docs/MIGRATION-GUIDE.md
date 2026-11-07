# Migrate to Membrane 6.6.5

See `distribution/release-notes/6.6.5.md` for the complete list of changes.

## Admin console actions accept only POST

Admin console actions that change the router, e.g. `/admin/service-proxy/save`, `/admin/proxy/delete`
or `/admin/node/up`, answer `405` unless they use POST. Switch scripts that call them to POST and
keep the parameters in the query string:

```sh
curl -X POST -u admin:membrane "localhost:9000/admin/node/down?balancer=Default&cluster=Default&host=node1.example.com&port=8080"
```

`node/up`, `node/down`, `node/takeout` and `node/reset` answer `404` for an unknown cluster or node
instead of adding it. Add nodes with `node/save` or the `clusterNotification` plugin.

## Cluster manager accepts only POST

`clusterNotification` answers requests to `/clustermanager/up`, `/down` and `/takeout` with `405`
unless they use POST. Switch scripts and node agents to POST and keep the parameters in the query string:

```sh
curl -X POST "localhost:9010/clustermanager/up?host=node1.example.com&port=8080"
```

`down` and `takeout` answer `404` for a node that is not registered. Register it with `up` first.

## Admin console and cluster manager behind a TLS-terminating proxy

The admin console and the cluster manager compare a browser's `Origin` with the scheme, host and port
the request reached Membrane with. If a reverse proxy or servlet container terminates TLS in front
of Membrane, let it set `X-Forwarded-Proto: https`. If it rewrites `Host`, let it set
`X-Forwarded-Host` to the original host. Otherwise browsers that send `Origin` but no
`Sec-Fetch-Site` get `403` when they change state.

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

## `jwtSign`
- `expiryTime` has been renamed to `expirySeconds`. 
- HTTP response in case of JWT validation failure was changed to Problem JSON.