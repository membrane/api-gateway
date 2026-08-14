---
name: config-error-handling
description: Add or improve config validation and error messages for Membrane config elements (@MCElement classes) — throwing/wrapping ConfigurationException, deciding where to validate, and understanding why some config errors get a highlighted YAML snippet and others don't. Use whenever asked to add config validation, throw a ConfigurationException, make a config error message more informative, or explain why a config error shows/doesn't show the YAML `>` marker.
---

# Config Error Handling

## Two failure phases, two reporting paths

Membrane config errors surface differently depending on *when* they're thrown, and that's the
first thing to get right.

**Setter-time** — inside an `@MCAttribute`/`@MCChildElement` setter, during YAML property binding
(`annot/.../yaml/parsing/binding/PropertyBinder.java`, `populate()`). Any exception thrown here
gets wrapped into `ConfigurationParsingException` with a `ParsingContext` (source location)
attached, so `RouterCLI` renders a highlighted `>` YAML snippet pointing at the offending line.
Reflection wraps the real exception in `InvocationTargetException` first — `PropertyBinder`
unwraps it before extracting the message, so `cause.getCause()` (not `cause`) carries the real
message. Example: `WsuTimestampInterceptor.setTtl` (`core/.../soap/wsse/WsuTimestampInterceptor.java`)
catches `DateTimeParseException` from `Duration.parse` and rethrows a `ConfigurationException`
with a concrete valid-example ("PT5M").

**Init-time** — inside `interceptor.init(Router)`, called by `Router` *after* all YAML parsing has
finished. No `ParsingContext` exists at this point — full stop, this isn't fixable per-interceptor
without a cross-cutting change to plumb source location through `Router`/interceptor init.
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
- **Setter-time message propagation**: see `YAMLParsingErrorTest.attributeSetterExceptionMessageSurfaces`
  (`annot/src/test/java/.../YAMLParsingErrorTest.java`) for a regression-test template that
  confirms a setter's real exception message survives the `InvocationTargetException` unwrap
  instead of coming out `null`.
