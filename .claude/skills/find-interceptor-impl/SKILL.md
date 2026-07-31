---
name: find-interceptor-impl
description: Find the Java implementation class behind a Membrane interceptor or config element given its XML name (the @MCElement value), e.g. "which class implements the <groovy> interceptor?". Use whenever the user references an interceptor / plugin / config element by its proxies.xml tag name (groovy, apiKey, rewriter, log, ...) and wants the source file or class, or asks where an interceptor is implemented / defined / configured. Also use the reverse direction — going from a class name to its config element name.
---

# Find Interceptor Implementation

In Membrane, every interceptor (and most config elements) is wired to an XML
config tag by an `@MCElement(name = "...")` annotation on its Java class. The tag
`<groovy>` in `proxies.xml` is implemented by the class annotated
`@MCElement(name = "groovy")` — `GroovyInterceptor`. This skill maps between the
two directions.

## Name → implementation class (the common case)

Run the helper with the XML element name:

```bash
.claude/skills/find-interceptor-impl/find-interceptor-impl.sh groovy
```

It prints the class and file, searching every module's `src/main/java` (the
annotation is not limited to `core`):

```
Implementation class for <groovy>:
  GroovyInterceptor  ->  ./core/src/main/java/.../groovy/GroovyInterceptor.java
```

Notes on its behaviour, so you can trust the output:

- **Several results** — some names are declared on more than one class (a base
  plus an override, or per-module variants). The script lists all of them; pick
  by package/context.
- **No exact match** — it exits non-zero and suggests the closest declared names
  (handles typos like `grovy` → `groovy`). Re-run with the corrected name.
- `name` need not be the first annotation attribute (`@MCElement(component =
  false, name = "...")` is handled), and both `name="x"` and `name = "x"`
  spacings match.

## Class → element name (reverse direction)

If the user has a class and wants its config tag, read the class's `@MCElement`
annotation directly:

```bash
grep -n '@MCElement' core/src/main/java/.../GroovyInterceptor.java
```

The `name = "..."` value is the tag used in `proxies.xml`.

## When the annotation isn't where you expect

A class with no `@MCElement` of its own is not directly configurable — it's
usually an abstract base (e.g. `AbstractInterceptor`) or is exposed under a
parent element. In that case search the class hierarchy for the nearest subclass
that does carry an `@MCElement`, or grep for the element name in
`distribution/examples` / `proxies.xml` to see how it's actually used.
