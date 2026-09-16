# Membrane API Gateway Tutorial - OpenAPI

Learn how Membrane serves an OpenAPI description, validates requests and responses against it,
and rewrites the server URLs it advertises.

- Serving an OpenAPI description at `/api-docs` and trying it out in the Swagger UI
- Validating a JSON request against the schema and reading the resulting error
- Rewriting the `servers[].url` entries of a served OpenAPI document: automatically, pinned to
  explicit values, or overridden for a deployment behind a reverse proxy
- Validating `application/x-www-form-urlencoded` messages - the format HTML forms post - against
  the same kind of schema: required fields, types and constraints, just like for JSON

To begin, open [10-OpenAPI.apis.yaml](10-OpenAPI.apis.yaml) and follow the instructions in the
file.

## More

- [OpenAPI 3.2](v32) - the features added in OpenAPI 3.2 (the `QUERY` method, `itemSchema`,
  `in: querystring`, `xml.nodeType`) and how Membrane validates requests against them.

## Further reading

The [*API Gateway Handbook*](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf)
explains OpenAPI rewriting in more depth:

- [§6.2 OpenAPI URL Rewriting](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf#page=65)
  — why the gateway rewrites the `servers` section.
- [§28.2 Rewriting of OpenAPI Addresses](https://www.membrane-api.io/ebook/API-Gateway-Handbook-v2.0.0.pdf#page=216)
  — the Membrane configuration for default and explicit rewriting, including
  deployments behind a reverse proxy.
