# Membrane's Configuration Language

This is information for Membrane developers.

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

## Annotations used

* **`@MCElement(name="foo")`**

    * **XML:** Defines the tag name `<foo>` used in the configuration.
    * **YAML:** Defines the **key name** `foo:` used in a map or list.

  `@MCElement` can only be used on classes. If you write `<foo>` or `foo:` in the configuration, this will cause the Java Class to be instantiated at startup.

  If `@MCElement(name="foo",topLevel=true)` has `topLevel==true` (which it has by default), `<foo>`/`foo:` can be used as the top-level element in the configuration. The Foo instance will be created as a Spring Bean (in the XML case) and take part of its life cycle. The most basic example is using `<router>`/`router:` at the top level: This will create and start a `Router` instance which starts Membrane API Gateway.

  Top-Level elements can carry a unique `id` attribute. This will register the instance Spring Bean with this ID.

* **`@MCAttribute`**
  Can be used on Setter methods. The Java Type of the Setter's parameter must be a simple type, enum, String or a `@MCElement` annotated class.

    * **XML:** Maps to an attribute string `<tag attr="value" />`.
    * **YAML:** Maps to a key-value pair `attr: value`.

  For simple types, the setter can be invoked as shown in the example:

  ```yaml
  api:
    port: 2000
  ```

  For enums, the name of an enum constant can be used case-insensitively:

  ```xml
  <api language="groovy"/>
  ```

  Strings look like simple types in the Configuration Language:

  ```xml
  <api test="new Random().nextBoolean()" />
  ```

  Attributes with a `@MCElement` type effectively reference beans via their IDs.

* **`@MCChildElement`**
  Can be used on Setters. The parameter of the Setter must be either a `@MCElement` annotated class or a `List` thereof or an abstract class with `@MCElement` subclasses.

    * **XML:** Structurally nests XML Elements (tags).
    * **YAML:** Structurally nests YAML Objects or Lists (Sequences).

  **Example:**

  ```xml
  <api>
    <ssl/>
    <log/>
    <groovy/>
  </api>
  ```

  ```yaml
  api:
    ssl: {}       # Single Object child
    interceptors: # List child
      - log: {}
      - groovy: {}
  ```

  Both configuration snippets effectively result in:

  ```java
  var apiProxy = new ApiProxy();
  apiProxy.setSSLParser(new SSLParser());
  apiProxy.setFlow(Lists.of(new LogInterceptor(), new GroovyInterceptor()));
  ```

  The child elements MUST appear in the order specified by the numerical `order`.

* **`@MCTextContent`**
  Can be used once in a class annotated by `@MCElement`. It must be a setter with a `String` parameter.

    * **XML:** The text content constitutes the body of the tag: `<groovy>code</groovy>`.
    * **YAML:** YAML objects cannot have both children/attributes *and* a raw scalar body. Therefore, the content must be mapped to an **explicit key** derived from the setter method name (e.g., `setSrc` -\> `src`).
      ```yaml
      groovy:
        src: |
          println('Code')
      ```

* **`@Required`**
  Can be used on methods annotated by `@MCAttribute` or `@MCChildElement`. It indicates that a certain attribute/child MUST be present.

Annotations are collected from the whole class hierarchy, meaning that e.g. `@MCAttribute`s can be inherited.

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