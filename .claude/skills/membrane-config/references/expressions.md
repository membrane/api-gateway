# Expressions, templating & built-in functions

The schema tells you which elements exist; it does **not** tell you how their
expression/template strings are evaluated. That's decided in code, and it's the
main thing this skill would otherwise guess at. Three facts drive every snippet
that contains a `${...}`, a `test:`, or a `value:`:

1. **Which engine** evaluates the string (SpEL vs. Groovy vs. none).
2. **How you call a built-in function** in that engine (`user()` vs. `fn.user()`).
3. **Which variables** are in scope.

Get any of these wrong and you get a 500 `Template execution error` or a silent
empty value — not a schema error, so `validate_config.py` won't catch it.

Source of truth (verify here if unsure, these can evolve):
`core/.../lang/ScriptingUtils.java`, `core/.../lang/CommonBuiltInFunctions.java`,
`core/.../lang/spel/functions/SpELBuiltInFunctions.java`,
`core/.../lang/groovy/GroovyBuiltInFunctions.java`,
`core/.../lang/ExchangeExpression.java`,
`core/.../interceptor/lang/AbstractLanguageInterceptor.java` (default language),
`core/.../interceptor/templating/TemplateInterceptor.java` (template engine).

## Which engine does each element use?

| Element | Engine | Interpolation | Default language | Call a function |
|---|---|---|---|---|
| `setHeader`, `setBody`, `setProperty`, … (anything with a `language` attribute — confirm the element name with `describe_element.py`) | `ExchangeExpression` | `${...}` inside the string | **SpEL** (`groovy` / `jsonpath` / `xpath` via `language:`) | `${user()}` — direct |
| `if` `test`, `choose`/`case` `test` | `ExchangeExpression` | whole string is the expression (no `${}`) | **SpEL** | `user()` — direct |
| `template` (`src`/`location`) | Groovy **StreamingTemplateEngine** (or **XmlTemplateEngine** when `contentType` is XML) | `${...}` is Groovy | Groovy (fixed — **no** `language` attribute) | `${fn.user()}` — via `fn` |
| `static` (`src`) | none | **none** — text is emitted verbatim | — | not possible; use `template`/`setBody` |
| `groovy` (script interceptor) | Groovy script | `${}` in Groovy strings | Groovy | functions available via the `fn` binding |

The trap we keep hitting: **`template` is Groovy, `setBody`/`setHeader` default
to SpEL.** Same-looking `${user()}` works in `setBody` (SpEL resolves the
function directly) but throws `No signature of method ... .user()` in
`template`, where it must be `${fn.user()}`. If you just need a function result
in a response body, prefer `setBody` with SpEL — fewer surprises than `template`.

```yaml
# SpEL element — function called directly
- setBody:
    value: 'User: ${user()}, encoded: ${base64Encode("alice:secret")}'
    # language: spel   # default, shown for clarity

# Groovy template — function via fn., variables direct
- template:
    src: |
      User: ${fn.user()}
      Path: ${path}
```

## Calling built-in functions — the `fn.` rule

All three engines expose the **same function set** (they delegate to
`CommonBuiltInFunctions`). Only the call syntax differs:

- **SpEL / jsonpath / xpath contexts** (`setHeader`, `setBody`, `if`, `choose`,
  `target.url`, …): call the function **by name** — `user()`,
  `base64Encode('a:b')`, `hasScope('admin')`. The evaluation context injects the
  exchange argument; you pass only the visible parameters.
- **Groovy `template`**: call it on the **`fn`** object — `fn.user()`,
  `fn.base64Encode('a:b')`. `fn` is the binding key (`ScriptingUtils.BINDING`)
  holding a `GroovyBuiltInFunctions`. Bare `user()` does **not** resolve.

## Built-in function catalog

Defined once in `CommonBuiltInFunctions`, surfaced in each language. Signatures
below are the **caller-visible** ones (the exchange/context arg is implicit).

| Function | Returns | Notes |
|---|---|---|
| `user()` | String | Authenticated username (from `basicAuthentication`, JWT, API-key, …). `null` if unauthenticated. |
| `hasScope()` / `hasScope(scope)` / `hasScope(list)` | boolean | OAuth2/JWT scope checks. |
| `scopes()` / `scopes(securityScheme)` | List\<String> | All scopes. |
| `isBearerAuthorization()` | boolean | Is there a `Bearer` Authorization header. |
| `isLoggedIn(beanName)` | boolean | Session check against a session manager bean. |
| `getDefaultSessionLifetime(beanName)` | long | |
| `base64Encode(s)` | String | UTF-8 → Base64. **There is no `base64Decode`.** To decode, drop to the host language: in **SpEL** `new String(T(java.util.Base64).getDecoder().decode(x))` (SpEL is a `StandardEvaluationContext`, so `T(...)` works); in a **Groovy** template `new String(java.util.Base64.decoder.decode(x))` (Groovy has no `T(...)`). |
| `env(name)` | String | Environment variable — keep secrets out of the config file. |
| `urlEncode(s)` / `pathEncode(segment)` | String | URL / path-segment encoding. |
| `jsonPath(path)` | Object | Read a value from the JSON body. |
| `xpath(expr)` / `xpath(expr, ctx)` | Object | Evaluate XPath against the (XML) body or a node. |
| `toJSON(obj)` | String | Serialize to JSON. |
| `isJSON()` / `isXML()` | boolean | Content-type / body sniffing. |
| `weight(percent)` | boolean | True for ~`percent`% of calls — weighted routing / canary. |
| `escape(obj)` *(Groovy only)* | Object | Content-type-aware escaping inside templates. |

## Binding variables (in scope for expressions/templates)

From `ScriptingUtils.createParameterBindings`. Many have singular+plural
aliases — both work.

| Variable | What |
|---|---|
| `exchange` / `exc` | The `Exchange`. |
| `flow` | Current `Flow` (`REQUEST` / `RESPONSE` / `ABORT`). |
| `message` | Current message (request or response per flow). |
| `request` / `response` | The respective message (`response`/`statusCode` only in RESPONSE flow). |
| `body` | Lazy body (string-decoded on use). |
| `header` / `headers` | Header map — `header['Authorization']` or `header.Authorization`. |
| `cookie` / `cookies` | Cookie map. |
| `method` | HTTP method (REQUEST flow). |
| `path` | Request URI/path. |
| `param` / `params` | Query parameters (REQUEST flow). |
| `pathParam` | Path parameters — `pathParam.id`. |
| `statusCode` | Response status (RESPONSE flow). |
| `property` / `props` | Exchange properties map. **In a Groovy `template`, use `property` — NOT `properties`** (`properties` collides with a built-in Groovy meta-property and returns the wrong thing; it works fine in SpEL and the Groovy script interceptor). |
| `it` | Shortcut for the `it` exchange property (used by `for` loops). |
| `json` | Parsed JSON body — present only when the template/script actually references `json`. |
| `spring` / `registry` | Bean factory / registry (advanced). |
| `fn` | The Groovy built-in functions object (see the `fn.` rule above). |

## Quick worked example: read Basic-Auth username on the receiving side

Two equivalent ways, depending on which element you use:

```xml
<!-- Preferred: let basicAuthentication validate, then read user() -->
<basicAuthentication>
    <user name="alice" password="secret"/>
</basicAuthentication>
<template>
    <src>User: ${fn.user()}
Path: ${path}</src>
</template>

<!-- Manual decode in a Groovy template (no base64Decode builtin, no T(...) in Groovy) -->
<template>
    <src>User: ${new String(java.util.Base64.decoder.decode(header['Authorization'].split(' ')[1])).split(':')[0]}</src>
</template>

<!-- Same decode but as a SpEL element (note T(...), which is SpEL-only) -->
<setBody language="spel"
         value="User: ${new String(T(java.util.Base64).getDecoder().decode(header['Authorization'].split(' ')[1])).split(':')[0]}"/>
```

Prefer the `basicAuthentication` + `fn.user()` form — it's unambiguous and
sidesteps the SpEL-vs-Groovy `T(...)` difference entirely.
