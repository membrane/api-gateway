# Membrane API Gateway Tutorial - OpenAPI

Learn how Membrane validates incoming requests against an OpenAPI description.

This tutorial shows how Membrane validates `application/x-www-form-urlencoded` messages - the format
HTML forms post - against the schema declared in an OpenAPI description: required fields, types and
constraints, just like for JSON.

To begin, open [50-Form-URL-Encoded.apis.yaml](50-Form-URL-Encoded.apis.yaml) and follow the
instructions in the file.

## More

- [OpenAPI 3.2](v32) - the features added in OpenAPI 3.2 (the `QUERY` method, `itemSchema`,
  `in: querystring`, `xml.nodeType`) and how Membrane validates requests against them.
