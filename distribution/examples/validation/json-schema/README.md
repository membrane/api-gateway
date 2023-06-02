### JSON Schema Validation

To run this example you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already.

To run this example execute the following steps:

1. Go to the directory `examples/validation/json-schema`.

2. Start `service-proxy.bat` or `service-proxy.sh`.

3. Look at `schema2000.json` and compare the schema to `good2000.json` and `bad2000.json`.

4. Run `curl -d @good2000.json http://localhost:2000/` on the console. Observe that you get a successful response.

5. Run `curl -d @bad2000.json http://localhost:2000/`. Observe that you get a validation error response.



Keeping the router running, you can try a more complex schema.

1. Have a look at `schema2001.json`, `good2001.json` and `bad2001.json`.

2. Run `curl -d @good2001.json http://localhost:2001/`. Observe that you get a successful response.

3. Run `curl -d @bad2001.json http://localhost:2001/`. Observe that you get a validation error response.



Resources:
  http://tools.ietf.org/html/draft-zyp-json-schema-03

(The file schema2001.json is loosely based on chapter 3 of
http://tools.ietf.org/html/draft-zyp-json-schema-03 .)

---
See: 
- [validator](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/validator.htm) reference