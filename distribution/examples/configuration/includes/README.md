# Include List Example

This example shows how to split one Membrane configuration with `include`.

What is covered:

- include a single file (`includes/file.apis.yaml`)
- nested include (`includes/nested/nested.apis.yaml`)
- include a directory (`includes/directory`). Only `*.apis.yaml` and `*.apis.yml` are loaded 

Important path rule:

- Paths used inside included YAML files (for example OpenAPI `location`, template `src`, or other file-based references) are resolved relative to the main config file you start (`apis.yaml`), not relative to the included file.

Start:

```bash
cd examples/configuration/includes
./membrane.sh
```

Try:

```bash
curl http://localhost:2000/root
curl http://localhost:2000/from-file
curl http://localhost:2000/nested
curl http://localhost:2000/from-directory-a
curl http://localhost:2000/from-directory-b
curl -i http://localhost:2000/ignored
```

`/ignored` returns `404` because `ignored.yaml` does not match the include pattern.
