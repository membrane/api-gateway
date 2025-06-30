# Javadoc Format Description

This project uses a structured Javadoc format to document classes, methods, and fields:

| Tag            | Applies To       | Purpose / Content                                              |
|----------------|------------------|----------------------------------------------------------------|
| `@description` | Classes, Methods | Description of the element.                                    |
| `@default`     | Methods          | Default value if not explicitly configured.                    |
| `@example`     | Methods          | Example value or usage snippet.                                |
| `@topic`       | Classes          | Assigns a topic/category to group related classes in the docs. |

> **Note:**
> The table lists custom tags used in this project. In addition, all standard Javadoc tags (such as `@param`, `@return`,
> and `@throws`) are fully supported according to the official Javadoc specification.

See e.g. [CallInterceptor](../core/src/main/java/com/predic8/membrane/core/interceptor/flow/CallInterceptor.java) for usage.