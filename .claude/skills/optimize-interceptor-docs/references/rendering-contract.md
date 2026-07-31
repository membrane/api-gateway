# Rendering contract: how Javadoc becomes a reference page

Source of truth: `annot/src/main/java/com/predic8/membrane/annot/model/doc/Doc.java` (parser) and
`annot/.../generator/JsonSchemaGenerator.java` (consumer). Read this once; the constraints are
enforced at compile time and silently shape the rendered page.

## Recognized Javadoc tags

The parser scans for `@word` tags. Only these are kept, and they render in this fixed order
(`Doc.PRIORITY`):

| Tag            | Where it goes        | Meaning |
|----------------|----------------------|---------|
| `@topic`       | class                | Section the element is filed under, e.g. `3. Security and Validation`. Keep existing value. |
| `@description` | class, attr, child   | The prose. Primary content of the page / table cell. |
| `@example`     | attribute            | A representative value for the attribute. |
| `@default`     | attribute            | The default value when the attribute is omitted. |
| `@explanation` | class                | Optional longer note, rendered after the description. |
| `@deprecated`  | class, attr          | Marks the element/attribute deprecated. |
| `@yaml`        | class                | The runnable YAML example block. Wrap in `<pre><code> … </code></pre>`. |

Dropped silently (`Doc.NEGATIVE`): `@author`, `@param`, `@see`. Don't rely on them to carry doc content.

**Each tag may appear once.** A duplicate (two `@description` on the same element) produces a
compiler **warning** and the second is ignored.

## The XML-safety rule (the big gotcha)

Every tag's value is wrapped as `<tagname>VALUE</tagname>` and parsed with a StAX XML reader. If it
isn't well-formed XML, the build **fails** (or blanks the value). Consequences:

- **Close every tag.** Use self-closing form for voids: `<br/>`, `<header/>`. `<p>…</p>`, `<ul><li>…</li></ul>` must balance.
- **`{@code …}` is a hard error** — the parser explicitly rejects it (`@code not allowed!`). Use `<code>…</code>`.
- **Undeclared entities error out.** `&nbsp;`, `&copy;`, `&mdash;`, `&rarr;` → `Entity … not allowed`. The five XML built-ins (`&lt; &gt; &amp; &quot; &apos;`) are fine. Prefer plain ASCII; write a literal `<` as `&lt;`.
- Safe formatting vocabulary inside values: `<code>`, `<pre>`, `<b>`, `<i>`, `<p>`, `<br/>`, `<ul>/<li>`.
- A `<pre>` syntax sketch using `[ ]`, `|`, `<value>`, `-`, `...` contains no `<`-delimited tags of its own, so it is always safe. (Avoid putting `<name>` style angle-brackets inside `<pre>` unless you escape them as `&lt;name&gt;` — they'd otherwise be read as XML tags.)

## What is derived automatically (don't hand-write it)

The attribute table, the child list with `0..*` / `1` cardinalities, the `required` column, the
`$ref` row, and the auto "Syntax" section are generated from the annotations + JSON schema. Your job
is only the prose, the example, and (optionally) a clarifying `[] |` sketch inside `@description`.
Don't restate the type or cardinality in prose — it's already in the table.

## Before / after (apiKey)

The shipped `ApiKeysInterceptor` embeds **XML** examples in child `@description`s — exactly what to remove.

### setStores — before
```java
/**
 * @description Defines the API key stores used to resolve and authorize keys. Provide one or more child elements that
 * implement a store (e.g., file-based, in-memory. jdbc or mongodb). Scopes from multiple stores are combined.
 * <p>
 * Example:
 * </p>
 * <pre><code><apiKey>
 *   <yourFileStore src="classpath:keys.txt"/>
 *   <yourXmlStore  ref="sharedKeysBean"/>;
 * </apiKey></code></pre>
 */
@MCChildElement(allowForeign = true)
public void setStores(List<ApiKeyStore> stores) { … }
```
Problems: XML sample (against house style), a stray `;`, a typo (`in-memory.`), and prose that
restates cardinality the table already shows.

### setStores — after
```java
/**
 * @description Key stores that resolve a key to its scopes and authorize it. Scopes from all
 * configured stores are merged. A key unknown to every store is rejected as invalid.
 */
@MCChildElement(allowForeign = true)
public void setStores(List<ApiKeyStore> stores) { … }
```
The concrete store options (`keyFileStore`, `simpleApiKeyStore`, `jdbcApiKeyStore`, …) are their own
elements with their own pages; the YAML for them belongs in a class-level `@yaml`, not here.

### Class-level @description — tightening
Before: two long paragraphs mixing behaviour, scopes, and status codes.
After — lead with one standalone sentence, then only load-bearing facts:
```java
/**
 * @description Validates an API key extracted from each request and resolves its scopes from the
 * configured stores. On success it adds an <code>ApiKeySecurityScheme</code> with the scopes to the
 * <code>Exchange</code>; later plugins test them with <code>hasScope("…")</code>. A missing key
 * returns 401 and an invalid key 403 as Problem Details, unless <code>required</code> is
 * <code>false</code>, in which case requests pass and scopes are attached when a valid key is present.
 * @topic 3. Security and Validation
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - apiKey:
 *         required: true
 *         extractors:
 *           - header: X-Api-Key
 * </code></pre>
 */
```

### Attribute — terse cells
```java
/**
 * @description Whether a valid key is required. When <code>false</code>, keys are still extracted
 * and scopes attached, but requests without a valid key pass through.
 * @default true
 * @example false
 */
@MCAttribute
public void setRequired(boolean required) { … }
```

## Quick checklist before finishing

- [ ] First sentence of every `@description` stands alone and is present-tense, active.
- [ ] Exactly one class-level `@yaml`, runnable, in `api:`/`flow:` context.
- [ ] Zero XML config samples anywhere.
- [ ] `[] |` syntax sketch added where children/alternatives make the shape non-obvious.
- [ ] No `{@code}`, no undeclared entities, all tags closed, `<br/>` self-closed.
- [ ] No prose restating type/cardinality/required (the table covers it).
- [ ] `mvn -q -o -pl core compile` is clean.
