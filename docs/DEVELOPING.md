# Membrane Development Guide

This is information for Membrane developers.
This guide shows how membrane plugins work on a basic level. It should give an overview of how to write a plugin, what the configuration will look like and how to document it.

## Membrane's Configuration Language

Membrane API Gateway's configuration can be written in **XML** or in **YAML**.

Everything that is configurable is driven by Java Code annotations from the `annot` submodule. `annot` defines 7 annotations. Here, we document the most common ones and their mapping to both formats.

Let's actually look at a very small (simplified) example. The Getters for all Setters are omitted in this example for the sake of brevity, but MUST be present in real code.

```java
public enum Language { GROOVY, SPEL, ... }

@MCElement(name = "api")
public class ApiProxy {
  @MCAttribute
  public void setPort(int port) { ... }

  @MCAttribute
  public void setLanguage(Language language) { ... }
  
  @MCAttribute
  public void setTest(String test) { ... }

  @MCChildElement(order=10)
  public void setSSLParser(SSLParser sslParser) { ... }
  
  @MCChildElement(order=20)
  public void setFlow(List<Interceptor> flow) { ... }
}

@MCElement(name = "ssl")
public class SSLParser { ... }

public abstract class Interceptor { ... }

@MCElement(name = "log")
public class LogInterceptor extends Interceptor { ... }

@MCElement(name = "groovy", mixed = true)
public class GroovyInterceptor extends Interceptor {
    @MCTextContent
    public void setSrc(String src) { ... }
}
```

Means that you can configure Membrane in **XML** with:

```xml
<api port="2000" language="groovy" test="new Random().nextBoolean()">
    <ssl />
    <log/>
    <groovy>
        println('Here')
    </groovy>
</api>
```

Or (alternatively) in **YAML** with:

```yaml
api:
  port: 2000
  language: GROOVY
  test: "new Random().nextBoolean()"
  ssl: {}
  interceptors:
  - log: {}
  - groovy:
      src: |
        println('Here')
```

Therefore, while most of the project historically used XML, everything can be expressed in YAML.

## Annotations

Membrane’s configuration grammar is derived from Java annotations in the `annot` module.  
`@MCElement` defines which **elements** exist, and method-level annotations define how configuration values map to Java **properties**.

> Annotations are collected from the full class hierarchy (inheritance applies).

### Quick map

- **`@MCElement`** (class): defines an element (`<foo>` / `foo:`)
- **`@MCAttribute`** (setter): defines a scalar property (`attr="..."` / `attr: ...`)
- **`@MCChildElement`** (setter): defines nested elements / lists
- **`@MCTextContent`** (setter): defines XML text body (YAML via explicit key)
- **`@MCOtherAttributes`** (setter): collects arbitrary/dynamic attributes
- **`@Required`** (setter): marks attribute/child as required



## Class-level annotation `@MCElement`
Defines a configurable element and its representation in XML/YAML.

| Property            | Description                                                                                                                                                                                                                                                                                                                                                                  | Default            |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------|
| `name()`            | Defines the element name used in the configuration and must be set.                                                                                                                                                                                                                                                                                                          | - (required)       |
| `id()`              | Optional identifier for the element. Must be used when defining a local element (component=false) which uses the same `name` as a "global element" / component (component=true).                                                                                                                                                                                             | same as the `name` |
| `mixed()`           | Only relevant for classes using `@MCTextContent` and xml configuration: When using `@MCTextContent` in XML, setting this to true allows mixed content (text + embedded XML/HTML). Otherwise, embedded tags may be parsed as child elements, which leads to parsing errors.                                                                                                   | `false`            |
| `topLevel()`        | Whether the element can be defined at the top-level of the config.                                                                                                                                                                                                                                                                                                           | `false`            |
| `component()`       | If `true` (default), the element can be used as a component in the configuration (`<foo>` / `foo:`). The instance is created as a managed component (XML: Spring bean, participating in its lifecycle) and can be referenced from other config sections.                                                                                                                     | `true`             |
| `configPackage()`   | Package for grammar extensions (elements not shipped in Membrane core). Use this when you define custom `@MCElement`s in an external project (e.g. a WAR or private interceptor library). In such projects you typically have exactly one `@MCMain(outputPackage=..., targetNamespace=...)`. The `configPackage` on your extension elements must match that `outputPackage`. | `""`               |
| `noEnvelope()`      | Activates “no envelope” mode for YAML (items list represented directly, not wrapped by property name). No effect on XML.                                                                                                                                                                                                                                                     | `false`            |
| `excludeFromFlow()` | Whether the element should be configurable as part of the YAML interceptor flow.                                                                                                                                                                                                                                                                                             | `false`            |
| `collapsed()`       | Enables inline YAML configuration for elements with exactly one configurable value (either a single `@MCAttribute` or a single `@MCTextContent`), allowing a scalar form instead of a nested object.                                                                                                                                                                         | `false`            |

---
## Method-level annotations (setters)

Method-level annotations are placed on setter methods to declare which configuration properties exist and how they are mapped to XML and YAML.
For every annotated setter, a corresponding getter MUST be present.

---
### `@MCAttribute`

Marks a setter as a configurable **property**.

**Constraints**

* Setter parameter type must be: simple type / enum / `String` / or an `@MCElement` type.
* Enums: constant names can be used case-insensitively.

**Mapping**

* **XML:** `<tag attr="value" />`
* **YAML:** `attr: value`


| Property            | Description                                                                                                                | Default                                   |
|---------------------|----------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|
| `attributeName()`   | Overrides the config attribute name for the annotated setter. If empty, the name is derived from the method/property name. | The name is derived from the method name. |
| `excludeFromJson()` | If `true`, the attribute is omitted from the YAML configuration, while still being available in XML.                       | `false`                                   |

---
### `@MCChildElement`

Marks a setter as a configurable **child element**.

**Constraints**

* Setter parameter must be:

  * an `@MCElement` type, or
  * a `List<@MCElement>` / `Collection<@MCElement>`, or
  * an abstract base class with `@MCElement` subclasses.

**Mapping**

* **XML:** nested elements (tags)
* **YAML:** nested objects or sequences (lists)

| Property            | Description                                                                                                                                   | Default |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `order()`           | Order of child elements. Must be unique per parent. Lower values come first.                                                                  | `0`     |
| `allowForeign()`    | Allows the child element to come from a non-core/foreign schema (e.g., referencing a Spring bean like an `ssl` bean).                         | `false` |
| `excludeFromJson()` | If `true`, the child element is omitted from the JSON Schema (and therefore YAML/JSON configuration), while still being available in XML/XSD. | `false` |

**Ordering rule:** Child elements MUST appear in the numerical `order`.

---
### `@MCTextContent`

Maps text content for an element. Only relevant for XML configuration. 

**Constraints**

* Can be used **once** per `@MCElement` class.
* Must be a setter with a single `String` parameter.

**Mapping**

* **XML:** the tag body: `<groovy>println('code')</groovy>`
* **YAML:** text content cannot be a raw scalar body if the object also has children/attributes; therefore it is mapped to an **explicit key** derived from the setter name (e.g. `setSrc` → `src`):

  ```yaml
  groovy:
    src: |
      println('Code')
  ```

**Note:** `@MCElement(mixed=true)` is relevant here for XML when embedded markup should not be treated as child elements (which leads to errors).

---
### `@MCOtherAttributes`

Collects arbitrary, not explicitly modeled attributes in addition to declared `@MCAttribute`'s.

**Constraints**

* Must be a setter that takes a `Map`:

  * `Map<String, String>` for free-form string attributes, or
  * `Map<String, Object>` where each value must be a **component**.

---

### `@Required`

Can be used on methods annotated with `@MCAttribute` or `@MCChildElement`. Marks the property/child as required.

---

## Javadoc Format Description

This project uses a structured Javadoc format to document configuration **elements** (classes) and their **properties** (methods). The doc generator **only evaluates custom block tags** listed below. 
> Any plain text that is not part of one of these tags is ignored.


### 1) Supported custom Javadoc tags

The `Doc` parser accepts **only** these tags:

* `@topic`
* `@description`
* `@example`
* `@default`
* `@explanation`
* `@deprecated`
* `@yaml`

Ignored standard tags: `@author`, `@param`, `@see` (silently dropped).
Any other tag: **warning** (“Unknown javadoc tag”).

Rules:

* Each tag may appear **at most once** per element (duplicate → warning; later one ignored).
* `{@code ...}` inside any tag value → **error** (“@code not allowed!”).
* Any XML/HTML entities like `&nbsp;` → **error** (“Entity … not allowed.”). Use only `&lt; &gt; &amp; &quot; &apos;`.


### 2) Class-level docs (for `@MCElement` classes)

| Tag            | Notes                                                                                                     |
|----------------|-----------------------------------------------------------------------------------------------------------|
| `@topic`       | Used to group elements in “Topics” reference. If missing → element won’t appear in topic list.            |
| `@description` | Main description shown on the element page. If missing → no description.                                  |
| `@deprecated`  | Renders a “Deprecated” alert box.                                                                         |
| `@yaml`        | Used for an example YAML code block on the element page. Use `@yaml` with `<pre><code> ... </code></pre>` |
| `@explanation` | Deprecated. Use `@description` instead.                                                                   |

Example:

```java
/**
 * @topic 1. Proxies
 * @description Routes requests to an upstream service.
 * @yaml 
 * <pre><code>
 *  serviceProxy: {}
 * </code></pre>
 * @deprecated Use FooProxy instead.
 */
@MCElement(name="serviceProxy")
public class ServiceProxy { ... }
```

---

### 3) Method-level docs (for `@MCAttribute`, `@MCChildElement`, `@MCOtherAttributes`)

#### 3.1 `@MCAttribute` methods

**Rendered in HTML (attribute table):**

* `@description` → Description column
* `@default` → Default column
* `@example` → Examples column

If none of these tags are present, the HTML shows `-` for those fields.

```java
/**
 * @description Target URL.
 * @default (not set)
 * @example https://api.example.com
 */
@MCAttribute
public void setTarget(String target) { ... }
```

#### 3.2 `@MCChildElement` methods

Use **`@description`** to document the relationship and semantics of the child element(s) for that setter (purpose, constraints, ordering).

```java
/**
 * @description Child elements that define request handling. Order matters.
 */
@MCChildElement(order = 10)
public void setInterceptors(List<Interceptor> interceptors) { ... }
```

#### 3.3 `@MCTextContent` and `@MCOtherAttributes` methods

Use **`@description`** to describe:

* for `@MCTextContent`: what the text content represents and any constraints (format, allowed values, examples in prose)
* for `@MCOtherAttributes`: which additional attributes are accepted and how they are interpreted/forwarded

> Note: These method-level docs are written into the XSD (`<xsd:documentation>`), but the current HTML parser does not render them yet.

```java
/**
 * @description Accepts additional attributes and forwards them unchanged to the underlying component.
 */
@MCOtherAttributes
public void setOtherAttributes(Map<QName, String> attrs) { ... }
```

### 4) Content format rules (inside tag values)

Inside each custom tag value you may use **well-formed XML/HTML fragments**.

* Must be **well-formed** (closed tags, properly nested), otherwise compiler error and the value becomes empty.
* No HTML entities except the XML built-ins: `&lt; &gt; &amp; &quot; &apos;`
* `{@link ...}` is allowed but stays **plain text** (not converted to hyperlinks).
* For code: use `<pre><code>...</code></pre>` (do **not** use `{@code ...}`).
