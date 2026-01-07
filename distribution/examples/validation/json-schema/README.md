# Validation - JSON Schema (simple + schemaMappings)

This example now combines both:
- a simple JSON Schema validation setup, and
- a setup that uses `$id`/`$ref` URN-based references resolved via `schemaMappings`.

## Running the example

1. Go to the directory `<membrane-root>/examples/validation/json-schema`.
2. Start `membrane.cmd` (Windows) or `./membrane.sh` (Unix/macOS).

Simple validation:
- Inspect `schemas/schema2000.json`, then compare with `good2000.json` and `bad2000.json`.
- `curl -H "Content-Type: application/json" -d @good2000.json http://localhost:2000`
- `curl -H "Content-Type: application/json" -d @bad2000.json  http://localhost:2000`

Schema with `$ref` URNs resolved via schemaMappings:
- Inspect `schemas/schema2001.json` referencing `urn:app:base_def` and `urn:app:meta_def`.
- See the mapped schemas under `schemas/base.json` and `schemas/meta.json`.
- `curl -H "Content-Type: application/json" -d @good2001.json http://localhost:2001`
- `curl -H "Content-Type: application/json" -d @bad2001.json  http://localhost:2001`

## How it is done

Take a look at the `apis.yaml`. It includes two APIs for validation and one backend:
- Port 2000 validates requests against a standalone schema `schemas/schema2000.json`.
- The schemaMappings example (port 2001) loads `schemas/schema2001.json` which contains `$ref` URNs. Those URNs are mapped to local files via `schemaMappings` so validation can resolve them.

---
See:
- JSON Schema: https://json-schema.org/
- Membrane validator reference: https://membrane-api.io/docs/current/validator.html