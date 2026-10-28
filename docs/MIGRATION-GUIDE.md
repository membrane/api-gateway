# Migrate to Membrane 7.8

This guide explains how to migrate existing Membrane installations to **Membrane 7.8**.
See `distribution/release-notes/7.8.0.md` for the complete list of changes.

## Configuration errors at startup

Some mistakes that used to fail per request now stop Membrane from starting. Check the log after
the update:

| Log message                                                        | Fix                                                                                                                       |
|--------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `Paths ... of OpenAPI ... are identical, they differ only in parameter names.` | Use the same parameter names in equivalent OpenAPI paths (`/pets/{id}` and `/pets/{petId}`) or merge them.  |
| `Cannot use path ... as URI template`                              | Remove stray braces (`/a/{b}}`) and duplicate parameters (`/{id}/{id}`).                                                  |
| `One bean was asked. But found 2 beans of class ...XmlConfig`      | Merge the global `xmlConfig`s in `components` into one, or reference the others with `$ref` from the plugins that need them. |
| `Invalid target URL: ...`                                          | Fix the `url` of the `target`.                                                                                            |
| *(error about the missing Kafka extension)*                        | Put the Membrane Kafka Extension on the classpath when using `kafkaProducer` or `kafkaConsumer`.                          |

The OpenAPI path check only applies when requests or responses are validated. As a stopgap, turn
validation off for that OpenAPI.

## URI templates match literally

Paths with templates (`api` `path` and OpenAPI paths) no longer treat `.`, `+`, `$`, `(`, `)` or `*`
as regular expression characters: `/files/{name}.json` no longer matches `/files/aXjson`. If you
relied on a regular expression, set `isRegExp: true`:

```yaml
api:
  port: 2000
  path:
    uri: /files/.*\.json
    isRegExp: true
```

A `+` in a path parameter is no longer decoded to a space, and an unescaped `#` no longer matches a
path parameter.

## `rewriter` no longer rewrites target URLs with a path

A `target` `url` with a path, like `http://backend.example.com/api/orders`, is used as it is.
Mappings that matched the path of the target URL have no effect anymore; put the path into the
target URL instead. Redirect rules are matched against the request path, not the destination.

## XPath and JSONPath read the body according to its Content-Type

This affects `if`, `choose`, the API `test`, `for`, `setHeader`, `setProperty`, the `keyExpression`
of `rateLimiter` and `${...}` templates:

- XPath parses XML, JSONPath parses JSON. A body without Content-Type or with `text/plain` is parsed
  as well. Other media types and empty bodies are evaluated against an empty document/object.
- A body that does not parse is rejected with `400` (request) or `502` (response) instead of `500`.
- JSONPath returns `null` instead of `""` for a missing field.

Make sure clients send the right `Content-Type`, e.g. `curl -H 'Content-Type: application/json'`.

## `xpath()` uses the plugin's `xmlConfig`

The `xpath()` function of SpEL and Groovy now uses the `xmlConfig` of its plugin and falls back to
the global one only when the plugin has none. A prefix declared only in the global `xmlConfig` is
not found in a plugin with its own `xmlConfig`; declare it there as well.

## `APIsJSON` replaced by `apisJson`

`APIsJSON` is removed. The new `apisJson` plugin follows APIs.json 0.24:

| `APIsJSON`       | `apisJson`                                            |
|------------------|-------------------------------------------------------|
| `collectionName` | `name`                                                |
| `rootDomain`     | removed                                               |
| `apisJsonUrl`    | `baseUrl` (without it, URLs come from the `Host` header) |

```yaml
# before
APIsJSON:
  collectionId: shop
  collectionName: Example Shop
  rootDomain: example
  apisJsonUrl: https://api.example.com/apis.json

# now
apisJson:
  collectionId: shop
  name: Example Shop
  baseUrl: https://api.example.com
```

See `distribution/tutorials/discovery/10-APIs-JSON.yaml`.

## Routing

- A path with an encoded `;`, `/` or `\` (`%3B`, `%2F`, `%5C`) is rejected with `400` if decoding
  it would select another API, OpenAPI document or validated OpenAPI path.
- An absolute-form request (`GET http://a.example.com/orders`) is routed by the host of the request
  target instead of the `Host` header, and the `Host` header is replaced with it.
- A regular expression `path` (`isRegExp: true`) no longer ignores a trailing slash: `^/foo$`
  matches `/foo` but not `/foo/`. Write `^/foo/?$` to accept both. It is also no longer matched as
  a prefix: `/foo` no longer routes `/foo/bar`; write `/foo.*` for that.

## OpenAPI validation

- Properties not allowed by `additionalProperties: false` or `unevaluatedProperties: false` are
  reported one error per property, at the property (`REQUEST/BODY#/color`). Adapt clients that
  parse the error location or message.
- OpenAPI 3.1/3.2: `patternProperties` regexes are no longer anchored. Add `^` and `$` to match
  whole property names.
- OpenAPI 3.1/3.2: a boolean schema `false` now rejects every value, and `enum`/`const` are checked
  for every type. Requests that passed before may now be rejected.

## WSDL validation

The SOAP envelope structure is validated: the `Header` must come before the `Body`, only one
`Header` and one `Body`, both in the namespace of the `Envelope`, namespace-qualified header
blocks, and a `Fault` must be alone in the `Body`. Fix clients or backends that send such messages.

## Plugins

- `rateLimiter`: if the `keyExpression` fails or yields no value, the request is counted by client
  IP instead of a shared `unknown` key.
- `xmlContentFilter` and `soapStackTraceFilter`: a body that cannot be parsed (including any body
  with a `DOCTYPE`) is rejected with `400` (request) or `500` (response) instead of being forwarded
  unfiltered.

## WebSockets

With frame interceptors (`wsLog`, `wsStompReassembler`, `wsInterceptor`):

- Messages are limited to 16 MiB. Raise the limit with `maxMessageSize` on `webSocket` if needed.
- `permessage-deflate` is no longer negotiated. A `groovy` plugin that removed
  `Sec-WebSocket-Extensions` can be dropped.
- A failing interceptor closes the connection with status `1011`.
- Interceptors are initialized once at startup, not per connection. Custom `wsInterceptor`s must
  keep per-connection state elsewhere.

Upgraded connections are no longer closed by `connection.soTimeout` on the backend side.

## Java API (when using Membrane as a library)

- `WebSocketFrame.tryRead` takes the maximum payload size and throws `WebSocketFrameTooLargeException`.
- `WebSocketConnection.onMessage` is only called for text and binary messages;
  `WEBSOCKET_CLOSED_POLL_INTERVAL_MILLISECONDS` is removed.
- `StreamPumpStats` is per router: use `router.getStatistics().getStreamPumpStats()`.
- The `Router` parameter is removed from `TemplateExchangeExpression.newInstance`, the constructors
  of `XPathExchangeExpression` and `JsonpathExchangeExpression`, and `Target.applyModifications`.

---

# Migrate to Membrane 7.6

This guide explains how to migrate existing Membrane installations to **Membrane 7.6**.

## Interceptors: rejected requests now use `ABORT`, not `RETURN`

Built-in protection interceptors (e.g. `jsonRpcProtection`, `mcpProtection`) that reject a request
now return `Outcome.ABORT` instead of `Outcome.RETURN` from `handleRequest()`. With `ABORT`, the
already-invoked interceptors see `handleAbort()` instead of `handleResponse()`, and a rejected
request can no longer produce an unfiltered backend response — closing a gap where a failed
interceptor allowed the backend response to reach the client unfiltered.

If you have custom Java interceptors that branch on `Outcome.RETURN`/`Outcome.ABORT`, or that
inspect the exchange in `handleResponse()` expecting to see requests rejected by these
interceptors, update them to handle `handleAbort()` instead.

## RouterCLI: `generate-jwk` now restricts the private key file to its owner

`generate-jwk` now writes the private JWK file with owner-only permissions (`rw-------` on POSIX
systems), including when an existing file is replaced with `-overwrite`. Previously the file was
created with the process's default (often world-readable) permissions. If a script or deployment
step depends on the private key file being group- or world-readable, adjust its permissions
explicitly after generation instead of relying on the previous default.

---

# Migrate to Membrane 7.1

This guide explains how to migrate existing Membrane installations to **Membrane 7.1**.
Most breaking changes in **7.1** are **YAML-related**, so they mainly affect upgrades from **Membrane 7.x (YAML)** to **7.1**.

## Recommended Migration Strategy

1. **Start from your current version:**
    * **Coming from Membrane 6.x:** follow the [Migration Guide from Membrane 6.X to 7](#migration-guide-from-membrane-6x-to-7) first, then apply the 7.1 changes from this guide.
    * **Already on Membrane 7.x:** apply the changes from this guide.

2. **Decide on your configuration format**
    * **Staying on XML:** remove deprecated interceptors/syntax as listed and apply only the non-YAML changes.
    * **Using / moving to YAML:** migrate your YAML to the 7.1 syntax described here. If you’re coming from 6.x, use this guide as the “target YAML syntax”, but don’t skip the additional breaking changes covered in the [Migration Guide from Membrane 6.X to 7](#migration-guide-from-membrane-6x-to-7) .

## Removed Interceptors and Plugins

The following legacy interceptors have been removed:

| Removed                     | Replacement |
|-----------------------------|-------------|
| `MethodOverrideInterceptor` | -           |

Remove these elements from your configuration and replace them with modern equivalents.

## Groovy
- Return string from script does not set a content type of `text/html` anymore. User has to set the content type manually.

## ApiKey extractors:
- Replace `headerExtractor: \n    name: <value>` with `header: <value>`
- Replace `queryParamExtractor: \n    name: <value>` with `query: <value>`
```yaml
# before
apiKey: 
  extractors:
    - headerExtractor: 
        name: X-API-KEY
    - queryParamExtractor: 
        name: api-key

# now 
apiKey:
  extractors:
    - header: X-API-KEY
    - query: api-key
```

## fileUserDataProvider
- rename `fileUserDataProvider` to `htpasswdFileProvider`
- rename `htpasswdPath` to `location`
```yaml
# before
fileUserDataProvider:
  htpasswdPath: .htpasswd

# now
htpasswdFileProvider: 
  location: .htpasswd 
```

## YAML configuration syntax in list elements

* List items can now be written in *inline form* if the list accepts **exactly one** allowed element type (no polymorphic candidates) and the element is neither `collapsed` nor `noEnvelope`.
* The old wrapper form remains supported: `- <kind>: { ... }`.

```yaml
# before
properties:
  - property:
      name: driverClassName
      value: org.h2.Driver
  - property:
      name: url
      value: jdbc:h2:./membranedb;AUTO_SERVER=TRUE

# now 
properties:
  - name: driverClassName
    value: org.h2.Driver
  - name: url
    value: jdbc:h2:./membranedb;AUTO_SERVER=TRUE
```


## OpenApi
Renamed `specs` to `openapi`.

**Before:**
```yaml
# before
api:
    port: 2000
    specs:
      - openapi:
          location: fruitshop-api.yml
          validateRequests: "yes"

# now (using the inline list item form)
api:
  port: 2000
  openapi:
    - location: fruitshop-api.yml
      validateRequests: "yes"
```

## setCookie
- Use `noEnvelope` for `cookies` list
```yaml
# before
setCookies:
  cookies:
    - cookie:
        name: foo
        value: bar
        secure: false

# now (using the inline list item form)
setCookies:
  - name: foo
    value: bar
    secure: false
```

## balancer
- `clustersFromSpring` was renamed to `clusters`
- the extra `clusters` list layer was removed (you now define clusters directly)
```yaml
# before
balancer:
  clustersFromSpring:
    - clusters:
        - cluster:
            nodes:
              - node:
                  host: localhost
                  port: 4000
              - node:
                  host: localhost
                  port: 4001

# now (using the inline list item form)
balancer:
  clusters:
    - nodes:
        - host: localhost
          port: 4000
        - host: localhost
          port: 4001
```

## choose
- `cases:` is gone (the case list is now **noEnvelope** under `choose`)
- `otherwise` is now a **list item** inside `choose`
- Only **one** `otherwise` is allowed, and it must be the **last** item of the list
```yaml
# before
choose:
  cases:
    - case:
        test: foo
        flow:
          - log: {}
  otherwise:
    - return: {}

# now
choose:
  - case:
      test: foo
      flow:
        - log: {}
  - otherwise:
      - return: {}
```

## chainDef and chain
- `chainDef` was removed.
- Define chains with `chain` and reference them like any other component via `$ref`.
```yaml
# before
components:
  log:
    chainDef:
      flow:
        - log: {}
---
api:
  port: 2000
  flow:
    - chain:
        ref: '#/components/log'

```
```yaml
# now
components:
  log:
    chain:
      - log: {}
---
api:
  port: 2000
  flow:
    - $ref: '#/components/log'
```

### YAML configuration for Elements with exactly one configurable Attribute
Some Elements with **exactly one** configurable Attribute can now be configured inline. This **does not** apply to all elements with exactly one configurable Attribute.

The supported elements are: `api.description`, `publicURL`, `headerFilter.include`, `headerFilter.exclude`, `keyTable`, `scopeTable`, `accessControl.allow`, `accessControl.deny`, `counter`, `mutation`, `header` (ApiKey extractor), `query` (ApiKey extractor)

#### headerFilter
```yaml
# before
headerFilter:
  rules:
    - include:
        pattern: "X-XSS-Protection"
    - exclude:
        pattern: "X-.*"

# now 
headerFilter:
  rules:
    - include: "X-XSS-Protection"
    - exclude: "X-.*"
```

#### mutation
```yaml
# before
graphQLProtection: 
  disallow:
    - mutation: 
        name: foo

# now
graphQLProtection:
  disallow:
    - mutation: foo
```

#### counter
```yaml
# before 
counter: 
  name: mock1

# now
counter: mock1
```

#### keyTable
```yaml
# before
apiKey: 
  stores:
    - databaseApiKeyStore: 
        keyTable: 
          name: foo

# now
apiKey:
  stores:
    - databaseApiKeyStore:
        keyTable: foo
```

#### scopeTable
```yaml
# before
apiKey: 
  stores:
    - databaseApiKeyStore: 
        scopeTable: 
          name: foo

# now 
apiKey:
  stores:
    - databaseApiKeyStore:
        scopeTable: foo
```

#### publicURL
```yaml
# before
publicURL: 
  publicURL: /foo

# now
publicURL: /foo
```

#### api.description
```yaml
# before 
api: 
  description: 
    content: Demo Api

# now 
api:
  description: Demo Api
```

---

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

| Removed                   | Replacement                                                                 |
|---------------------------|-----------------------------------------------------------------------------|
| `gateKeeperClient`        | Gatekeeper is not supported anymore                                         |
| `wadl`                    | Use OpenAPI                                                                 |
| `xmlSessionIdExtractor`   | Use language based session handling. See tutorials/loadbalancing/30-Sticky-Sessions.yaml |
| `telekomSMSTokenProvider` | The SMS provider doesn't offer the needed SMS service anymore.              | 
| `groovyTemplate`          | `template` offers the same functionality including Groovy language support. |
| `urlNormalizer`           | -                                                                           |

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

---

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