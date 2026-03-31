# Include List Example

This example shows how to split one Membrane configuration with `include`.

What is covered:

- include two APIs from separate folders
- each included API uses a template file from its own folder
- a fallback API in `apis.yaml` that returns `404` with a `notfound` message

Start:

```bash
cd examples/configuration/include
./membrane.sh
```

Try:

```bash
curl http://localhost:2000/customers
curl http://localhost:2000/orders
curl -i http://localhost:2000/does-not-exist
```

`/does-not-exist` returns `404` with:

```json
{"message":"notfound"}
```
