# Validate OpenAPI 3.2 Descriptions

Membrane understands [OpenAPI 3.2](https://spec.openapis.org/oas/v3.2.0) descriptions and can validate
requests against them — including the constructs that 3.2 adds on top of 3.1:

- **`itemSchema` for sequential media types**: every item of a streaming body (here `application/jsonl`,
  JSON Lines) is validated individually against a schema.
- **The `QUERY` HTTP method**: a read operation that carries a request body.
- **The `in: querystring` parameter**: the entire query string described as one typed value.

The API in [`bulk-api-3.2.yml`](bulk-api-3.2.yml) uses all three.

## Running the example

Use Membrane version 6 or newer.

1. Go to the `examples/openapi/validation-3.2` directory.

2. Start Membrane:

```
./membrane.sh
```

or on Windows:

```
membrane.cmd
```

3. Send a **valid** JSON Lines stream. Each line conforms to the `Document` schema, so the request is
   forwarded to the backend:

```shell
curl -X POST http://localhost:2000/documents \
  -H "Content-Type: application/jsonl" \
  --data-binary $'{"id": "1", "title": "First"}\n{"id": "2", "title": "Second"}'
```

You get the backend's answer:

```json
{
  "success" : true
}
```

4. Now send a stream where the **second line is missing the required `title`**:

```shell
curl -X POST http://localhost:2000/documents \
  -H "Content-Type: application/jsonl" \
  --data-binary $'{"id": "1", "title": "First"}\n{"id": "2"}'
```

Membrane rejects the request and points at the offending item with a JSON Pointer (`/1/title`):

```json
{
  "title": "OpenAPI message validation failed",
  "type": "https://membrane-api.io/problems/user/validation",
  "validation": {
    "method": "POST",
    "uriTemplate": "/documents",
    "path": "/documents",
    "errors": {
      "REQUEST/BODY#/1/title": [ {
        "message": "Required property title is missing.",
        "complexType": "Document",
        "schemaType": "object"
      } ]
    }
  }
}
```

## How it works

`swagger-parser`, the library Membrane builds on, does not support OpenAPI 3.2 yet. Membrane therefore
ships a dedicated `OpenAPI32Parser` that reads 3.2 documents, parses the large 3.1-compatible subset with
the established engine, and re-attaches the new 3.2 constructs (the `query`/`additionalOperations`
operations and `itemSchema`) so that routing and validation can use them.

See also the [validation-simple](../validation-simple) and [validation](../validation) examples.
