# XML Namespaces Example

Namespaces are a powerful XML feature. Unfortunately, they can be a bit tricky to use. This example shows 
how to declare and use namespaces. The namespace declarations can be used in:

- setProperty
- setHeader
- if
- case
- target url
- api test
- call url

The example shows how to declare and use them with XPath expressions.

## Running the Sample
***Note:*** *The requests are also available in the requests.http file.*

1. **Navigate** to the `examples/xml/namespaces` directory.
2. **Start** the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Run**:
    - Send:
      ```bash
      curl -d @person.xml localhost:2000
      ```
4. **Understand**:
    Take a look at the `proxies.xml` file.
