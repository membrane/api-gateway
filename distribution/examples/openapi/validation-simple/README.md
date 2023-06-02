# Validate Requests and Responses against OpenAPI

Membrane can validate requests and responses against OpenAPI descriptions. The specifications can be in YAML or JSON format, on disk or reachable over the network.

## Running the example

Use Membrane version 5 or newer.

1. Go to the _examples/openapi/validation-simple_ directory

2. Start Membrane:

```
./service-proxy.sh
```

or:

```
service.proxy.bat
```

3. Send a request using curl:

```shell
curl -X POST http://localhost:2000/persons \
  -H "Content-Type: application/json" \
  -d '{"name": "Johannes Gutenberg","age": 78}'
```

As the request is exactly as specified in the OpenAPI you should get the answer from the backend:

```json
{
  "success" : true
}
```

4. Now send an invalid request:

```shell
curl -X POST http://localhost:2000/persons \
  -H "Content-Type: application/json" \
  -d '{"name": "Johannes Gutenberg","age": -10}'
```

5. Have a look at the validation error in the response.

```json
{
  "method" : "POST",
  "uriTemplate" : "/persons",
  "path" : "/persons",
  "validationErrors" : {
    "REQUEST/BODY#/age" : [ {
      "message" : "-10 is smaller than the minimum of 0",
      "complexType" : "Person",
      "schemaType" : "integer"
    } ]
  }
}
```

You can also execute the requests in the _requests.http_ file.


## How it works

1. In the _proxies.xml_ configuration there is an **OpenAPIProxy** that reads the OpenAPI document and creates the APIs in Membrane.   

```xml
<api port="2000">
    <openapi location="contacts-api-v1.yml" validate="requests"/>
</api>
```

2. Have a look at the OpenAPI document _contacts-api-v1.yml_. The _age_ property must be 0 or higher.

```yaml
age:
  type: integer
  minimum: 0
```

3. Incomming requests are validated against the definitions in the OpenAPI specification. How things evolve is dependend on the result of the validation. 

**a.) There are no validation errors**

The request is sent to the backend with the address from the OpenAPI definition:

```yaml
info:
  ...
servers:
  - url: http://localhost:3000
```

Then Membrane routes the answer of the backend back to the client.

**b.) There are validation errors**

In case of a validation failure an error message is returned to client without calling the backend. 

```json
{
  "method" : "POST",
  "uriTemplate" : "/persons",
  "path" : "/persons",
  "validationErrors" : {
    "REQUEST/BODY#/age" : [ {
      "message" : "-10 is smaller than the minimum of 0",
      "complexType" : "Person",
      "schemaType" : "integer"
    } ]
  }
}
```

See the [openapi-validation](../validation) folder for a more detailed example.

---
See:
- [openapi](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/openapi.htm) reference