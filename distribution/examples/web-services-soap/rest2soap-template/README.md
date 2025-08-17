# SOAP to REST Example

This example shows how to expose a simple **REST API** that calls an existing **SOAP service** and transforms the result back into JSON.  
It demonstrates how Membrane can be used for **legacy integration** scenarios where clients prefer JSON/REST but the backend only provides SOAP.

## Try It Out

1. Open a terminal at the `examples/web-services-soap/rest2soap-template` directory.

2. Execute `membrane.sh` or `membrane.cmd`

3. Call the REST endpoint:

   ```bash
   curl "http://localhost:2000/cities/Paris"
   ```

4. You will receive a JSON response with the country and population extracted from the SOAP service:

   ```json
   {
     "country": "France",
     "population": "11346800"
   }
   ```

5. Have a look at `proxies.xml` to see how it works.

## Use Cases

* Exposing legacy SOAP services as REST/JSON APIs.
* Gradual migration from SOAP to REST without rewriting backend systems.
* Simplifying integration for clients that cannot handle SOAP.