# Membrane configuration cheatsheet

Common, copy-adaptable patterns drawn from `distribution/tutorials`. These show
idiom and shape — always confirm exact attribute names with
`describe_element.py` and validate the result. Tutorials are the source of truth
for style; the schema is the source of truth for what's allowed.

## Skeleton

Every YAML config starts with the schema header (gives editors autocompletion)
and has exactly one top-level key per document:

```yaml
# yaml-language-server: $schema=https://www.membrane-api.io/v7.2.3.json
api:
  port: 2000
  target:
    url: https://api.predic8.de
```

Top-level keys (one per `---` document): `api`, `global`, `configuration`,
`soapProxy`, `sslProxy`. `api` is by far the most common.

## Multiple APIs on one port

APIs are matched top-to-bottom; the first whose `path` matches wins. An API with
no `path` matches everything remaining (put it last as a catch-all). Separate
documents with a line containing only `---`.

```yaml
api:
  port: 2000
  path:
    uri: /shop
  target:
    url: https://api.predic8.de
---
api:
  port: 2000
  target:                 # no path -> catch-all
    url: https://httpbin.org
```

## The flow: request / response plugins

`flow:` is an ordered list. A bare plugin runs in both directions; wrap it in
`request:` or `response:` to scope it. Plugins execute in order on the way in,
and in reverse on the way out.

```yaml
api:
  port: 2000
  flow:
    - request:
        - log: {}
        - setHeader:
            name: X-Trace
            value: ${uuid()}
    - response:
        - setHeader:
            name: X-Powered-By
            value: Membrane
  target:
    url: https://api.predic8.de
```

## Conditionals

`if` (single condition) and `choose`/`case`/`otherwise` (branching). Default
expression language is `spel`; set `language:` (`groovy`, `jsonpath`, `xpath`)
to switch. Available test variables include `header['X']`, `path`, `method`,
`statusCode`, `body`.

```yaml
- if:
    test: header['X-Id'] == null
    flow:
      - static:
          src: Please set X-Id header
      - return:
          status: 400
```

```yaml
- choose:
    - case:
        test: headers['X-Foo'] != null
        flow:
          - return:
              status: 200
    - otherwise:
        - return:
            status: 400
```

## Returning a response without a backend

Omit `target:` and end the flow with `return`. Use `static` (or `template`,
`setBody`) to set the body.

```yaml
api:
  port: 2000
  flow:
    - static:
        src: "Hello!"
    - return:
        status: 200
```

## Expressions & templating

`${...}` interpolates inside string values. The language follows `language:` on
the surrounding element (default `spel`). For request transformation, jsonpath
reads the body, e.g. `${$.fieldName}`.

> For anything involving a built-in function (`user()`, `base64Encode`,
> `hasScope`, …), the SpEL-vs-Groovy call difference (`${user()}` vs.
> `${fn.user()}`), or which variables are in scope, see
> [expressions.md](expressions.md). The schema does not cover this and it's a
> common source of runtime 500s.

```yaml
target:
  method: GET
  url: https://api.predic8.de/shop/v2/products?sort=${$.sort}&limit=${$.limit}
  language: jsonpath
```

## TLS termination

```yaml
api:
  port: 8443
  ssl:
    key:
      private:
        location: membrane-key.pem
      certificates:
        - location: membrane.pem
  target:
    url: https://api.predic8.de
```

## Basic authentication

```yaml
- basicAuthentication:
    users:
      - username: alice
        password: "qwertz"
```

## OpenAPI publish + validation

```yaml
api:
  port: 2000
  openapi:
    - location: ../../conf/openapi/fruitshop-v2-2-0.oas.yml
```

## Rate limiting (global)

`global:` applies plugins to every API in the config.

```yaml
global:
  flow:
    - rateLimiter:
        requestLimit: 10
        requestLimitDuration: PT1M   # ISO-8601 duration
```

---

## XML format (proxies.xml) — only when explicitly requested

Modern configs use YAML. The legacy Spring XML format is still supported. Same
elements, camelCase tags, attributes instead of nested keys. The router/global
wrapper is required:

```xml
<spring:beans xmlns="http://membrane-soa.org/proxies/1/"
    xmlns:spring="http://www.springframework.org/schema/beans"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
                        http://membrane-soa.org/proxies/1/ http://membrane-soa.org/schemas/proxies-1.xsd">
  <router>
    <global>
      <rateLimiter requestLimit="10" requestLimitDuration="PT1M"/>
    </global>
    <api port="2000">
      <path>/reset-pwd</path>
      <static>Success</static>
      <return/>
    </api>
  </router>
</spring:beans>
```

Find XML examples under `distribution/examples/**/proxies.xml`. The
`validate_config.py` script validates YAML only — for XML, ground every element
in an existing `proxies.xml` example.
