# Validation - JSON Schema with Schema Mappings

This sample explains how to set up and use the `validator` plugin with JSON Schemas that reference external schemas 
by `$ref` URN/ID, and how to map those references to local files via `schemaMappings`.

## Running the Example

1. Go to the directory:
   `<membrane-root>/examples/validation/json-schema/schema-mappings`

2. Start `membrane.cmd` or `membrane.sh`.

3. Look at `schemas/schema2000.json` and note the `$ref` to `urn:app:base_parameter_def`. Then open `schemas/base-param.json` and compare the schema to `good2000.json` and `bad2000.json`.

4. Run `curl -H "Content-Type: application/json" -d @good2000.json http://localhost:2000/` on the console. Observe that you get a successful response.

5. Run `curl -H "Content-Type: application/json" -d @bad2000.json http://localhost:2000/`. Observe that you get a validation error response.

Keeping the router running, you can try a more complex setup with multiple referenced schemas.

1. Have a look at `schemas/schema2001.json` and note the `$ref`s to `urn:app:base_parameter_def` and `urn:app:meta_def`. Then open `schemas/base-param.json` and `schemas/meta.json` and compare the schemas to `good2001.json` and `bad2001.json`.

2. Run `curl -H "Content-Type: application/json" -d @good2001.json http://localhost:2001/`. Observe that you get a successful response.

3. Run `curl -H "Content-Type: application/json" -d @bad2001.json http://localhost:2001/`. Observe that you get a validation error response.

## How it is done

In `proxies.xml`, each API configures a `<validator jsonSchema="...">`.
The root schemas contain `$ref` references to URN/IDs, for example:

- `urn:app:base_parameter_def#/$defs/BaseParameter`
- `urn:app:meta_def#/$defs/Meta`

To let Membrane resolve these URNs, you map them in the validator using:

```xml
<schemaMappings>
  <schema id="urn:app:base_parameter_def" location="schemas/base-parameter.json"/>
  <schema id="urn:app:meta_def" location="schemas/meta.json"/>
</schemaMappings>
````

Only if validation succeeds, the request is forwarded to the backend (port 2002).

---

See:

* [JSON Schema](https://json-schema.org/) documentation
* [validator](https://www.membrane-api.io/docs/current/validator.html) reference