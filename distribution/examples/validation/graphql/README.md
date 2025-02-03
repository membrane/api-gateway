# Validation - GraphQL

Check GraphQL-over-HTTP requests, enforcing several limits and restrictions to mitigate the attack surface of GraphQL APIs.


## Running the Example

1. **Navigate** to the `examples/validation/graphql` directory in a terminal.
2. **Start** the API Gateway by executing `service-proxy.sh` (Linux/Mac) or `service-proxy.ps1` (Windows).
3. **Query** the protected API by opening the `requests.http` file in VS Code or IntelliJ IDEA to execute the querries.

## How it works

Let's examine  the `proxies.xml` file.

```xml
<router>
  <api port="2000">
    <request>
      <graphQLProtection maxRecursion="2" />
    </request>
    <target url="https://www.predic8.de/fruit-shop-graphql" />
  </api>
</router>
```

We define an `<api>` component with the `graphQLProtection` plugin inside, running on port 2000 and routing to [Fruit Shop GraphQL](https://www.predic8.de/fruit-shop-graphql).  
We validate requests only; using the default configuration of the plugin (except for recursion):
- Don't allow GraphQL extensions.
- Only allow queries over HTTP methods `GET` and `POST`.
- Max recursion depth of 2 (so it catches before nesting depth in this example).
- Max nesting depth of 7.
- Maximum number of mutations in a query of 5.

---
See:
- [graphQLProtection](https://www.membrane-api.io/docs/current/graphQLProtection.html) reference for detailed configuration