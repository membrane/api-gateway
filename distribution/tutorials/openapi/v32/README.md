# Membrane API Gateway Tutorial - OpenAPI 3.2

This tutorial covers the features added in [OpenAPI 3.2](https://spec.openapis.org/oas/v3.2.0) and
how Membrane validates requests against them. Each step has its own OpenAPI 3.2 description and its
own self-teaching configuration:

- The `QUERY` HTTP method (a read operation with a request body)
- `itemSchema` for sequential media types (validating each item of a JSON Lines stream)
- The `in: querystring` parameter (validating the whole query string as one typed value)
- The `xml.nodeType` keyword (mapping schema properties to XML attributes, elements or text)

To begin, open [10-QUERY-Method.apis.yaml](10-QUERY-Method.apis.yaml) and follow the instructions in
the file.
