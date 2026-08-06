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

```text
Implementation class for <groovy>:
  GroovyInterceptor  ->  ./core/src/main/java/.../groovy/GroovyInterceptor.java
```

## Class → element name (reverse direction)

Given a class name, find its file and annotation in one shot — no need to know
which module it lives in first:

```bash
grep -rn --include='GroovyInterceptor.java' '@MCElement' .
```

The `name = "..."` value is the tag used in `proxies.xml`. No match means the
class isn't directly configurable — it's likely an abstract base or exposed
under a parent element; check its subclasses for the nearest one carrying
`@MCElement`, or grep `distribution/examples` / `proxies.xml` for how it's
actually used.

## Behaviour to trust

- **Several results** — some names are declared on more than one class (a base
  plus an override, or per-module variants); the script lists all of them, pick
  by package/context.
- **No exact match** — exits non-zero and suggests the closest declared names
  (handles typos like `grovy` → `groovy`); re-run with the corrected name.
- **Matching is lenient**: `name` need not be the first annotation attribute
  (`@MCElement(component = false, name = "...")` is fine), and both `name="x"`
  and `name = "x"` spacings match.
