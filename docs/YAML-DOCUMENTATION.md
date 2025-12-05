# Membrane's Configuration Language

This is information for Membrane developers.

Membrane API Gateway's configuration can be written in **XML** or in **YAML**.

## 1\. Mapping Java to XML/YAML

Let's look at a simplified example.

```java
public enum Language { GROOVY, SPEL, ... }

@MCElement(name = "api")
public class ApiProxy {
  @MCAttribute
  public void setPort(int port) { ... }

  @MCAttribute
  public void setLanguage(Language language) { ... }
  
  // Order 10
  @MCChildElement(order=10)
  public void setSSLParser(SSLParser sslParser) { ... }
  
  // Order 20
  @MCChildElement(order=20)
  public void setFlow(List<Interceptor> flow) { ... }
}
```

This single Java class definition supports **both** formats:

**XML Representation:**

```xml
<api port="2000" language="groovy">
    <ssl />
    <log/>
</api>
```

**YAML Representation:**

```yaml
api:
  port: 2000
  language: GROOVY
  ssl: {}
  interceptors:
    - log: {}
```

## 2\. Annotations & Behavior

* **`@MCElement(name="foo")`**

    * **XML:** Defines the tag name `<foo>`.
    * **YAML:** Defines the key name `foo:` in a map or list.
    * *Top Level:* If `topLevel=true`, it can start a configuration file (e.g., `<router>` or `router:`).

* **`@MCAttribute`**

    * **XML:** Maps to an attribute string `<tag attr="value" />`.
    * **YAML:** Maps to a key-value pair `attr: value`.
    * *Note:* Simple types, Enums, and Strings map directly.

* **`@MCChildElement`**

    * **XML:** Maps to nested child tags.
    * **YAML:** Maps to nested Objects or Lists (Sequences).
    * *Ordering:* The `order` attribute dictates the sequence in XML and the expected order in the YAML list structure.

* **`@MCTextContent`**

    * **XML:** The body text of the tag: `<groovy>println "hi"</groovy>`.
    * **YAML:** Must be mapped to an **explicit property key** (derived from the setter name), as YAML objects cannot have both fields and scalar bodies. e.g., `groovy: { src: "println 'hi'" }`.

## 3\. Javadoc Format Description

This project uses a structured Javadoc format to generate documentation for both XML and YAML users.

| Tag | Applies To | Purpose / Content | Content Format |
| :--- | :--- | :--- | :--- |
| **`@description`** | Classes, Methods | Description of the element. <br><br>**IMPORTANT:** Write this **neutrally**. Do not use XML-specific jargon like "child tag" or "attribute". Use generic terms like "property", "element", or "nested object" so it makes sense for YAML users too. | HTML / Text |
| **`@default`** | Methods | Default value. | String |
| **`@example`** | Methods | **XML** usage example. | XML Snippet |
| **`@yaml`** | Methods | **YAML** usage example. This is the YAML equivalent of `@example`. | YAML Snippet |
| **`@topic`** | Classes | Assigns a topic/category to group related classes in the docs (e.g. "1. Proxies"). | Number + Title |

### Documentation Guidelines

**1. The `@description` Trap**
Do not write: "The `<ssl>` tag must be a child of `<api>`."
Do write: "The SSL configuration is defined within the API object."
*Why?* Because YAML users don't see tags. If you hardcode XML structure in the text, you confuse half your audience.

**2. `@example` vs. `@yaml` Consistency**
If you provide an `@example` (XML), you **should** provide a `@yaml` counterpart.

* Ensure both snippets demonstrate the *exact same configuration logic*.
* If you change one, you must update the other.

**3. YAML Formatting**

* YAML relies on whitespace. When writing code inside the `@yaml` tag, use `<pre>` blocks or standard code block formatting if supported by the parser to preserve indentation.
* Do not leave YAML examples malformed; a YAML snippet with wrong indentation is worse than no example at all.

**4. Handling `@MCTextContent` in Docs**
When documenting a class with `@MCTextContent`:

* **XML Example:** Show the text inside the tag.
* **YAML Example:** Explicitly show the key required to hold that text (e.g., `src: | ...`). This is a common tripping point for users switching from XML to YAML.

### Example Javadoc

```java
/**
 * @description
 * Configures the Groovy interceptor. Allows execution of arbitrary scripts 
 * during the request/response flow.
 *
 * @default (not set)
 *
 * @example
 * <groovy>
 * println("Hello XML")
 * </groovy>
 *
 * @yaml
 * groovy:
 * src: |
 * println("Hello YAML")
 */
@MCElement(name = "groovy", mixed = true)
public class GroovyInterceptor { ... }
```