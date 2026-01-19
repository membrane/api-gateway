# Migration Guide from Membrane 6.X to 7

This guide describes how to migrate existing Membrane installations from version 6 to version 7.  
Membrane 7 introduces a new YAML based configuration model and a significantly modernized internal architecture.  
XML based configurations remain supported, but some legacy features and APIs have been removed.

## 1. Recommended Migration Strategy

- Upgrade Membrane to 7.x with your existing XML configuration. Remove all deprecated interceptors and syntax listed below.
- Try to startup Membrane with the old configuration and look at logs and potential configuration errors.
- Gradually migrate APIs from XML to YAML using the tutorials as templates or keep the XML configuration.
- Update scripts, monitoring and CI pipelines to reflect the new JMX and configuration model.

## 2. Configuration Model

- YAML as First Class Configuration Format
- Membrane 7 introduces a new YAML based configuration format that replaces most use cases of `proxies.xml`.
- You are encouraged to migrate to YAML, but existing XML configurations continue to work.
- To use existing XML configurations, just remove the `apis.yaml` file in the `conf` directory. Most of the XML configurations will work without changes.


### 2.1 Router Configuration
Router settings are now configured via the `configuration` element.

Example:
```xml
<!-- before -->
<router production="true" />

<!-- now -->
<router>
    <configuration production="true" />
</router>
```

## 3. Removed Interceptors and Plugins

The following legacy interceptors have been removed:

| Removed | Replacement                                                                 |
|--------|-----------------------------------------------------------------------------|
| `gateKeeperClient` | Gatekeeper is not supported anymore                                         |
| `wadl` | Use OpenAPI                                                                 |
| `xmlSessionIdExtractor` | Use language based session handling. See examples/loadbalancing/4-session   |
| `telekomSMSTokenProvider` | The SMS provider doesn't offer the needed SMS service anymore.              | 
| `groovyTemplate` | `template` offers the same functionality including Groovy language support. |
| `urlNormalizer` | -                                                                           |

Remove these elements from your configuration and replace them with modern equivalents.

## 4. AccessControl

* The ACL for `accessControl` is now defined inline in the YAML/XML configuration (no external `acl.xml`).
* ACL rules do not match URIs/paths anymore. Use separate API entries for different paths and define `accessControl` per API.
* Rules match only the peer IP/CIDR (IPv4/IPv6) or hostname (regex). First match wins. If nothing matches, access is denied.

Example:

```yaml
api:
  port: 2000
  path:
    uri: /foo
  flow:
    - accessControl:
        - allow: "^localhost$"
---
api:
  port: 2000
  path:
    uri: /bar
  flow:
    - accessControl:
        - deny: 127.0.0.1
        - allow: ::1
```
    

## 5. Internal Routing

Instead of:

```xml
<target url="service:a"/>
```

use:

```xml
<target url="internal://a"/>
```

## 6. Logging

Instead of `<log headerOnly="true"/>` use `<log body="false"/>`.

## 7. Scripting

### 7.1 Groovy Expressions

`${header}` now returns a `Map<String,String>` instead of an object of class `Header`. Use `$header['x-foo']` instead of `$header.getFirstValue('x-foo')`.

Instead of:

```groovy
headers.getFirst("X-My-Header")
```

use now:

```
headers['X-My-Header']
```

## 7.2. SpEL Expressions

- `headers.foo` delivered only the first value of the header `foo`. Now it returns a comma separated list of values.
- A nonexisting header like `header['x-unknown']` returned an empty string. Now it returns `null`.

## 8. JMX

The JMX ObjectName format has changed to: `io.membrane-api:00=routers, name=`


## 9. Java Interfaces

- `ValidatorInterceptor` the `FailureHandler` has been removed. Logging and error handling must now be implemented directly inside validators.
- The return type of `HttpClient#call` has changed. The `HttpClient` call method no longer returns an `Exchange`. Use the one from the parameters instead, it is the same instance.

    ```java
    // Membrane 6
    public Exchange call(Exchange exc) throws Exception;
    
    // Membrane 7
    public void call(Exchange exc) throws Exception;
    ```
- `HttpClientInterceptor.setAdjustHeader(boolean)` has been removed. Header adjustment is now configured via `HttpClientConfiguration`.

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