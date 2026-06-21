---
name: optimize-interceptor-docs
description: Rewrite the reference documentation of a Membrane config element so the page generated at membrane-api.io comes out clean, exact, and reference-style. Use whenever the user wants to write, improve, optimize, polish, or review the docs / Javadoc / @description / @yaml example of an interceptor, plugin, or any @MCElement class (e.g. "document the apiKey interceptor", "the rewriter docs are weak", "add a YAML example to <groovy>", "clean up the @description on these attributes"). The deliverable is edits to the Java source's class- and setter-level Javadoc — short prose, a runnable YAML example, a `[] |` syntax sketch, and no XML samples. Reach for this even when the user only describes the element and the doc problem without saying "skill".
---

# Optimize Interceptor Documentation

Membrane's reference pages (https://www.membrane-api.io/docs/) are generated **from the Java source**, not hand-written. The element name, attribute table, child cardinalities and "required" flags are derived from the `@MCElement` / `@MCAttribute` / `@MCChildElement` annotations and the JSON schema. The *prose* and the *example* come from Javadoc tags. So "writing docs" here means rewriting Javadoc — and getting it right depends on knowing exactly what the renderer consumes and what it rejects.

Goal: a page that reads like a precise reference entry — a one-glance summary, exact behaviour, one runnable YAML example, and a compact syntax sketch. Short but complete. **No XML examples.**

## Workflow

1. **Read the class and pull facts from code — never invent.** Identify:
   - `@MCElement(name=...)` → the element name used in YAML.
   - every `@MCAttribute` setter → an attribute (default from the field initializer or `@default`; type from the setter param; enum values from the enum type).
   - every `@MCChildElement` setter → a child element / list (cardinality from `List<…>` vs single, order from `order=`).
   - `handleRequest` / `init` → real behaviour worth documenting: status codes, side effects (what it adds to the `Exchange`), what happens on the failure path, defaults applied when config is omitted.
   - which flows it actually acts in — check `getAppliedFlow()` and which of `handleRequest`/`handleResponse`/`handleAbort` are really overridden (and any `flow.isRequest()` guards inside them). An element can be wired for a flow but no-op there; document what runs, not what's declared. **Only mention flow in the prose when it's special** — i.e. the element acts in *only* the request flow or *only* the response flow. Acting in both request and response is the normal case; don't state it.
   - **Don't trust the existing Javadoc — it's often copy-pasted from a sibling element and wrong.** Verify every `@default` against the field initializer and every `@description` against the code (e.g. `<for>` shipped `@default groovy` for a field initialized to `SpEL`, and an `in` description that talked about a "test condition" lifted from `<if>`). Fix what the code contradicts.
2. **Rewrite the class-level Javadoc**: `@topic`, `@description`, `@yaml`. Keep the existing `@topic` unless it's clearly wrong.
3. **Rewrite each attribute and child setter's Javadoc**: `@description`, plus `@default` / `@example` for attributes.
4. **Replace every XML sample with YAML**, and add a `[] |` syntax sketch where the shape is non-trivial.
5. **Validate** — every tag value is parsed as XML by the build (see below). Confirm well-formedness; optionally compile to let the annotation processor check it.

See `references/rendering-contract.md` for the exact tag list, render order, the XML-safety rules, and a full apiKey before/after. Read it before your first rewrite — the XML-parsing constraint is non-obvious and easy to break.

## What good looks like

**`@description` (the prose).** First sentence is a crisp, present-tense statement of what the element does — it appears as the page intro and in tooltips, so it must stand alone. Follow with only the 1–3 facts a user needs to use it correctly: the default behaviour, status codes, what it writes to the exchange, what a downstream plugin can rely on. Active voice, no "this interceptor will…", no marketing. If a fact isn't in the code, don't claim it.

**Don't duplicate attribute prose in the class `@description`.** Each attribute (or child element) documents its own behaviour in its `@MCAttribute` / `@MCChildElement` setter `@description`; the per-attribute detail renders right next to the class description in the same page. If a fact is specific to one attribute (e.g. how `method` decides whether to send a body, what values `url` accepts, how the `root` name is derived), state it **only** on that setter and keep the class `@description` about element-level behaviour — do not restate it at the top. Mention an attribute by name at the class level only when it's load-bearing for understanding the whole element. The setter `@description` is the place for the full rule, including its default-derivation logic; the `@default` / `@example` tags then carry the short value.

**`@yaml` (the example).** Exactly one, minimal but runnable, shown in real context (`api:` → `flow:` → the element). Show the common case, not every option. YAML never contains `<`, so it's always XML-safe.

```
api:
  port: 2000
  flow:
    - apiKey:
        required: true
        extractors:
          - header: X-Api-Key
```

**Syntax sketch (`[] |` notation).** When the element nests children or offers alternatives, give a compact grammar so the shape is clear at a glance. Put it in `@description` inside a `<pre>` block (no `<` characters inside, so it stays XML-safe). Notation:
- `<value>` — a placeholder the user fills in
- `[ x ]` — optional
- `a | b` — choose one
- a `-` list with `...` — repeatable

```
apiKey:
  [ required: true | false ]      # default: true
  extractors:                     # 0..*; defaults to one header extractor
    - header: <name> | query: <name>
    ...
  stores:                         # 0..*
    - ...
```

**Attributes.** One or two sentences in `@description`; `@default` carries the default value, `@example` a representative value. Keep them terse — they render as table cells.

**Linking to examples/tutorials.** Check whether the element has runnable examples or tutorials and point to them — use the `find-example` skill (`.claude/skills/find-example/find-example.sh <name>`), which searches both `distribution/examples` and `distribution/tutorials`. `@see` does **not** work — it's in `Doc.NEGATIVE` and is dropped from the rendered page. Mention the path in the `@description` prose instead, as `ReturnInterceptor` does ("See the examples under examples/orchestration."). For **examples** (each is a directory) point at the directory, not a specific file: `examples/scripting/groovy`. For **tutorials** (each is a single `.yaml` file) reference the file itself, since there's no per-element directory: `tutorials/advanced/70-Scripting-Groovy.yaml`. Prefer the tutorial that *teaches* the element (its title usually names it) over ones that only use it incidentally.

## Verifying

The annotation processor parses each tag value as XML at compile time, so it catches malformed markup, `{@code}`, and undeclared entities for you:

```
mvn -q -o -pl core compile
```

(Compile only — don't run the `-am` test suite; per project memory it hits a locale-dependent test failure unrelated to docs.) A clean compile means the Javadoc will render. To inspect the generated schema text, set `MEMBRANE_GENERATE_DOC_DIR` before the build.
