# Javadoc Format Description

This project uses a structured Javadoc format to document classes, methods, and fields:

| Tag            | Applies To       | Purpose / Content                                                                                                                                                                                                                                                                          | Content                                            |
|----------------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------|
| `@description` | Classes, Methods | Description of the element. (Longer text for classes, shorter for methods. The name of the @MCElement annotation (for classes) and the name of the method (for methods) is obvious: "setHttpClientConfiguration" does NOT need to be documented with "sets the HTTP Client Configuration". | XML snippet (especially: well-formed)              |
| `@default`     | Methods          | Default value. This often duplicates the Java Field initializer or effective result of the init() method.                                                                                                                                                                                  | String                                             |
| `@example`     | Methods          | Example value or usage snippet.                                                                                                                                                                                                                                                            | String or XML Snippet                              |
| `@topic`       | Classes          | Assigns a topic/category to group related classes in the docs. The topics (e.g. "1. Proxies and Flow") are numbered. The title ("Proxies and Flow") should be consistently assigned to the number ("1.") across all files.                                                                 | Number, followed by a dot and a string (the title) |

**Additional Guidelines for `@default` tag:**

*   If an `@MCAttribute` method does not have an explicit default value in its corresponding Java field initializer or `init()` method, and its absence implies a specific behavior (e.g., 'not set', 'uses system default', 'no value'), use `(@default (not set))` or a similarly descriptive phrase.
*   If the attribute is mandatory and has no default, the `@default` tag can be omitted.

> **Note:**
> The table lists custom tags used in this project. In addition, all standard Javadoc tags (such as `@param`, `@return`,
> and `@throws`) are fully supported according to the official Javadoc specification.

See e.g. [CallInterceptor](../core/src/main/java/com/predic8/membrane/core/interceptor/flow/CallInterceptor.java) for usage.