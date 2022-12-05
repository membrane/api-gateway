# Validate Requests and Responses against OpenAPI

The **OpenAPIProxy** can validate requests and responses against OpenAPI. The specifications can be in YAML or JSON format on disk or reachable over the network.


## RUNNING THE EXAMPLE

1. Go to the _examples/openapi/openapi-validator-simple_ directory

2. Start Membrane:

```
./service-proxy.sh
```

or:

```
service.proxy.bat
```

3. Send a valid request using curl:

```
curl -X POST http://localhost:2000/persons -H "Content-Type: application/json" -d '{"name": "Johannes Gutenberg","age": 78}'
```

As the request is exactly as specified in the OpenAPI you should get the answer from the backend:

```
{
  "success" : true
}
```

4. Now send an invalid request:

```
curl -X POST http://localhost:2000/persons -H "Content-Type: application/json" -d '{"name": "Johannes Gutenberg","age": -10}'
```

5. Have a look at the validation error in the response.

```
{
  "validationErrors" : [ {
    "message" : "-10 is smaller than the minimum of 0",
    "validationContext" : {
      "method" : "POST",
      "path" : "/persons",
      "uriTemplate" : "/persons",
      "schemaType" : "integer",
      "complexType" : "Person",
      "validatedEntityType" : "BODY",
      "validatedEntity" : "REQUEST",
      "statusCode" : 400,
      "jsonpointer" : "/age"
    }
  }
}
```

You can also execute the requests in the _requests.http_ file.


#### HOW IT IS DONE

1. In the _proxies.xml_ configuration there is an OpenAPIProxy that reads the OpenAPI document and creates the APIs in Membrane.   

```
<OpenAPIProxy port="2000">
    <spec location="contacts-api-v1.yml" validate="requests"/>
</OpenAPIProxy>
```

2. Incomming requests are validated against the definitions in the OpenAPI specification. In case of an validation failure an error message is returned. 

3. The request is sent to the backend server with the server url from the OpenAPI definition:

```
info:
  ...
servers:
  - url: http://localhost:3000
```

4. The answer is returned to the client.

For a more detailed example have a look at the _examples/openapi/openapi-validator_ folder.