# Validation - JSON Schema 

This sample explains how to set up and use the `validation` plugin within a `soapProxy` component.


## Running the Example

1. Go to the directory `examples/validation/json-schema`.

2. Start `service-proxy.bat` or `service-proxy.sh`.

3. Look at `schema2000.json` and compare the schema to `good2000.json` and `bad2000.json`.

4. Run `curl -d @good2000.json http://localhost:2000/` on the console. Observe that you get a successful response.

5. Run `curl -d @bad2000.json http://localhost:2000/`. Observe that you get a validation error response.



Keeping the router running, you can try a more complex schema.

1. Have a look at `schema2001.json`, `good2001.json` and `bad2001.json`.

2. Run `curl -d @good2001.json http://localhost:2001/`. Observe that you get a successful response.

3. Run `curl -d @bad2001.json http://localhost:2001/`. Observe that you get a validation error response.

## How it is done

Let's examine  the `proxies.xml` file.

```xml
<router>
  <api port="2000">
    <request>
      <validator jsonSchema="schema2000.json" />
    </request>
    <target host="localhost" port="2002" />
  </api>

  <api port="2001">
    <request>
      <validator jsonSchema="schema2001.json" />
    </request>
    <target host="localhost" port="2002" />
  </api>

  <api port="2002">
    <groovy>
      Response.ok("&lt;response&gt;good request&lt;/response&gt;").build()
    </groovy>
  </api>
</router>
```

We define three `<api>` components, running on ports 2000-2002.
The first two validate all requests using the JSON schema defined in the `<validator />` component's `jsonSchema` attribute.  
The successfully validated requests then get sent to the third `<api>` component, where we simply return a 200 "Ok" response.

---
See: 
- [JSON Schema](https://json-schema.org/) documentation
- [validator](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/validator.htm) reference