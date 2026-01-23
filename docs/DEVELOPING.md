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
public LogInterceptor extends Interceptor { ... }

@MCElement(name = "groovy", mixed = true)
public GroovyInterceptor extends Interceptor {
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

This section describes how Membrane configuration is mapped to Java classes via annotations.

### Overview

- **Class annotation** defines *elements* (tags / YAML objects).
- **Setter annotations** define how config maps to properties:
  - **Attributes** (`@MCAttribute`)
  - **Child elements** (`@MCChildElement`)
  - **Text content** (Only relevant for xml configuration) (`@MCTextContent`)
  - **Other / dynamic attributes** (`@MCOtherAttributes`)
- `@Required` can be used on `@MCAttribute` / `@MCChildElement`.

TODO


Annotations are collected from the whole class hierarchy, meaning that e.g. `@MCAttribute`s can be inherited.


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

TODO short description

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

Maps text content for an element. Only relevant for xml configuration. 

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

Can be used on methods annotated by `@MCAttribute` or `@MCChildElement`. Marks the property/child as required.

---

## Javadoc Format Description

This project uses a structured Javadoc format to document classes, methods, and fields. Since we support both XML and YAML, documentation **must be format-agnostic** where possible.

| Tag | Applies To | Purpose / Content | Content |
| :--- | :--- | :--- | :--- |
| `@description` | Classes, Methods | Description of the element. <br><br>**Important:** Do not refer to XML-specific structures (like "tag" or "attribute") in the text. Use neutral terms like "element", "property", or "child". | HTML / Text |
| `@default` | Methods | Default value. | String |
| `@example` | Methods | **XML** example value or usage snippet. | XML Snippet |
| `@yaml` | Methods | **YAML** example value or usage snippet. This should demonstrate the exact same logic as the XML `@example`. | YAML Snippet |
| `@topic` | Classes | Assigns a topic/category to group related classes in the docs (e.g. "1. Proxies"). | Number + Title |

**Comments:**

* Do not use Markdown in Javadocs. Javadocs are HTML.
* When you refer to elements from the Configuration Language in Javadocs, do not refer to them verbatim (e.g. `<api>`), but XML escape the reference: `&lt;for&gt;`.
* For `@yaml` examples, ensure indentation is preserved (e.g., wrap in `<pre>` blocks if necessary for the doc generator).

**Additional Guidelines for `@default` tag:**

* If an `@MCAttribute` method also has `@Required`, the `@default` Javadoc tag does not make sense. It should therefore be omitted.
* If an `@MCAttribute` method does not have an explicit default value in its corresponding Java field initializer or `init()` method, and its absence implies a specific behavior (e.g., 'not set', 'uses system default', 'no value'), use `(@default (not set))` or a similarly descriptive phrase.
* If the attribute is mandatory and has no default, the `@default` tag can be omitted.

> **Note:**
> The table lists custom tags used in this project. In addition, all standard Javadoc tags (such as `@param`, `@return`,
> and `@throws`) are fully supported according to the official Javadoc specification.

See e.g. [CallInterceptor](../core/src/main/java/com/predic8/membrane/core/interceptor/flow/CallInterceptor.java) for usage.