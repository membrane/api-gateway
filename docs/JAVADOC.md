# Membrane's Configuration Language

This is information for Membrane developers.

Membrane API Gateway's configuration can be written in XML or (experimentally) in YAML.

Everything that is configurable is driven by Java Code annotations from the `annot` submodule. `annot` defines 7 annotations. Here, we document the most common ones.

Let's actually look at a very small (simplified) example. The Getters for all Setters are omitted in this example for the sake of brevity, but MUST be present in real code.

```
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
  public void setInterceptors(List<Interceptor> interceptors) { ... }
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

Means that you can configure Membrane in XML with:
```xml
<api port="2000" language="groovy" test="new Random().nextBoolean()">
    <ssl />
    <log/>
    <groovy>
        println('Here')
    </groovy>
</api>
```
Or (alternatively, experimentally) in YAML with:
```yaml
api:
  port: 2000
  language: GROOVY
  test: "new Random().nextBoolean()"
  interceptors:
  - log: {}
  - groovy:
      src: |
        println('Here')
```

## Annotations used
* `@MCElement(name="foo")` means that you can write `<foo>` somewhere in the configuration where it fits.

  `@MCElement` can only be used on classes. If you write `<foo>` in the configuration, this will cause the Java Class to be instantiated at startup.

  If `@MCElement(name="foo",topLevel=true)` has `topLevel==true` (which it has by default), `<foo>` can be used as top-level element in the configuration. The Foo instance will be created as a Spring Bean (in the XML case) and take part of its life cycle. The most basic example is using `<router>` at the top level in the XML file: This will create and start a `Router` instance which starts Membrane API Gateway.

  Top-Level elements can carry an unique `id` attribute (e.g. top-level `<foo id="foo">`). This will register the instance Spring Bean with this ID. The bean can be referenced using `<spring:bean ref="foo"/>` in places where you can and want use the bean.
 
* `@MCAttribute` can be used on Setter methods. The Java Type of the Setter's parameter must be a simple type, enum, String or a `@MCElement` annotated class.

  It for simple types, the setter can be invoked as shown in the example:
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
  
  Attributes with a @MCElement type effectively reference beans via their IDs:
  ```xml
  <foo bar="springBeanId" />
  ```
  The bean with the `springBeanId` must in this case be defined top-level and carry the ID.
* `@MCChildElement` can be used on Setters. The parameter of the Setter must be either a `@MCElement` 
 annotated class or a `List` thereof or an abstract class with `@MCElement` subclasses.
  This structurally nests XML Elements/YAML Objects in the Configuration Language:
  ```xml
  <api>
    <ssl/>
    <log/>
    <groovy/>
  </api>
  ```
  ```yaml
  api:
    ssl: {}
    interceptors:
      - log: {}
      - groovy: {}
  ```
  Both configuration snippets effectively result in
  ```java
  var apiProxy = new ApiProxy();
  apiProxy.setSSLParser(new SSLParser());
  apiProxy.setInterceptors(Lists.of(
    new LogInterceptor(),
    new GroovyInterceptor()
  ));
  ```
  The child elements MUST appear in the order specified by the numerical `order`. The order of a `@MCChildElement` in a class must be unique.

* `@MCTextContent` can be used once in a class annotated by `@MCElement`. It must be a setter with a `String` parameter.
* `@Required` can be used on methods annotated by `@MCAttribute` or `@MCChildElement`. It indicates that a certain attribute/child MUST be present.
  
Annotations are collected from the whole class hierarchy, meaning that e.g. `@MCAttribute`s can be inherited.

## Javadoc Format Description

This project uses a structured Javadoc format to document classes, methods, and fields:

| Tag            | Applies To       | Purpose / Content                                                                                                                                                                                                                                                                          | Content                                            |
|----------------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------|
| `@description` | Classes, Methods | Description of the element. (Longer text for classes, shorter for methods. The name of the @MCElement annotation (for classes) and the name of the method (for methods) is obvious: "setHttpClientConfiguration" does NOT need to be documented with "sets the HTTP Client Configuration". | XML snippet (especially: well-formed)              |
| `@default`     | Methods          | Default value. This often duplicates the Java Field initializer or effective result of the init() method.                                                                                                                                                                                  | String                                             |
| `@example`     | Methods          | Example value or usage snippet.                                                                                                                                                                                                                                                            | String or XML Snippet                              |
| `@topic`       | Classes          | Assigns a topic/category to group related classes in the docs. The topics (e.g. "1. Proxies and Flow") are numbered. The title ("Proxies and Flow") should be consistently assigned to the number ("1.") across all files.                                                                 | Number, followed by a dot and a string (the title) |

**Additional Guidelines for `@default` tag:**

* If an `@MCAttribute` method also has `@Required`,the `@default` Javadoc tag does not make sense. It should therefore be omitted.
*   If an `@MCAttribute` method does not have an explicit default value in its corresponding Java field initializer or `init()` method, and its absence implies a specific behavior (e.g., 'not set', 'uses system default', 'no value'), use `(@default (not set))` or a similarly descriptive phrase.
*   If the attribute is mandatory and has no default, the `@default` tag can be omitted.

> **Note:**
> The table lists custom tags used in this project. In addition, all standard Javadoc tags (such as `@param`, `@return`,
> and `@throws`) are fully supported according to the official Javadoc specification.

See e.g. [CallInterceptor](../core/src/main/java/com/predic8/membrane/core/interceptor/flow/CallInterceptor.java) for usage.