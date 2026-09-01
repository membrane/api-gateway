---
name: config-error-handling
description: Add or improve config validation and error messages for Membrane config elements (@MCElement classes) — throwing/wrapping ConfigurationException, deciding where to validate, and understanding why some config errors get a highlighted YAML snippet and others don't. Use whenever asked to add config validation, throw a ConfigurationException, make a config error message more informative, or explain why a config error shows/doesn't show the YAML `>` marker.
---

# Config Error Handling

## Two failure phases, two reporting paths

Membrane config errors surface differently depending on *when* they're thrown, and that's the
first thing to get right.

**Setter-time** — inside an `@MCAttribute`/`@MCChildElement` setter, during YAML property binding
(`annot/.../yaml/parsing/binding/PropertyBinder.java`, `populate()`). That `catch (Throwable cause)`
re-wraps into `new ConfigurationParsingException(cause.getMessage())` and attaches a
`ParsingContext` (source location), so `RouterCLI` renders a highlighted `>` YAML snippet pointing
at the offending line. Mind the message: reflection wraps a setter's own exception in
`InvocationTargetException`, and `PropertyBinder` does *not* unwrap it, so the message it copies is
the wrapper's (`null`) — the real text sits in `cause.getCause().getMessage()`. The
`@MCElement(collapsed=true)` scalar path in `ObjectBinder` does unwrap (`e.getTargetException()`).
Example: `WsuTimestampInterceptor.setTtl` (`core/.../soap/wsse/WsuTimestampInterceptor.java`)
catches `DateTimeParseException` from `Duration.parse` and rethrows a `ConfigurationException`
with a concrete valid-example ("PT5M").

**Init-time** — inside `interceptor.init(Router)`, reached via `DefaultRouter.init()` →
`AbstractRouter.initProxies()` → `proxy.init(this)`, i.e. *after* all YAML parsing has finished.
The YAML bootstrap does carry a `ParsingContext` into the bean registry, but nothing on this
`initProxies()` path passes it on, so an interceptor's `init()` has no source location to report —
not fixable per-interceptor without a cross-cutting change to plumb it through `Router`/interceptor
init.
`ConfigurationException` thrown here is caught by
`SpringConfigurationErrorHandler.handleConfigurationException`
(`core/.../exceptions/SpringConfigurationErrorHandler.java:128`), which prints a plain
`"************** Configuration Error ***..."` / `"Reason: <cause message>"` block — no YAML
snippet. This is the existing, accepted behavior for every interceptor's init()-time checks
(missing keystore, bad key alias, unsupported algorithm, ...), not a bug to chase per class.

Prefer failing at init()-time (config-load time) over lazily at first-request time — see
`DigitalSignatureInterceptor.validateConfiguration()` for the pattern (checked in `init()`, before
any request is handled).

## Writing a good ConfigurationException message

- Name the bad attribute and the value that was given.
- List valid options built from real constants, not hand-typed strings, so the list can't drift
  from what's actually supported — e.g. `DigitalSignatureInterceptor.SUPPORTED_DIGEST_ALGORITHMS`
  is built from `javax.xml.crypto.dsig.DigestMethod.*`, verified experimentally against the JDK's
  DOM `XMLSignatureFactory` rather than assumed from the constant list.
- Wrap the causing checked exception with `new ConfigurationException(message, cause)` rather than
  swallowing it — `cause.getMessage()` is still visible via `Reason:` in the init-time path.

## Testing pattern

- **Init-time checks**: follow `DigitalSignatureInterceptorTest`'s `initWith(references...)` +
  `assertThrows(RuntimeException.class, () -> initWith(...))` idiom — set the invalid state on a
  fresh interceptor with an otherwise-valid config, assert init throws.
- **Setter-time message propagation**: `annot/src/test/java/.../YAMLParsingErrorTest.java` builds
  ad-hoc `@MCElement` classes and asserts on the rendered error report (see `attribute()`) — use
  that as the template. There is currently no test covering a setter-thrown exception's message,
  which is why the `InvocationTargetException` gap above goes unnoticed; add one there if you touch
  that path.
