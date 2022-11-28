# Validate Requests and Responses against OpenAPI Specification


## RUNNING THE EXAMPLE

1. Go to the _examples/openapi/openapi-validator-simple_ directory

2. Start Membrane with the script inside this directory:

    ./service-proxy.sh

or

    service.proxy.bat

3. Send a valid request using curl:

    curl -X POST http://localhost:2000/persons  -d '{"name": "Johannes Gutenberg","age": 78}'

You should get an answer.

4. Now send an invalid request:

    curl -X POST http://localhost:2000/persons   -d '{"name": "Bo","email": "mailatme","age": "old"}'

5. Have a look at the error messages in the response.


#### HOW IT IS DONE

1. In the _proxies.xml_ configuration there is an OpenAPIProxy that reads the OpenAPI document and creates the APIs in Membrane.   

```
    <OpenAPIProxy port="2000">
        <spec location="contacts-api-v1.yml" validate="requests"/>
    </OpenAPIProxy>
```

2. Incomming requests are validated against the definitions in the OpenAPI specification.

For a more detailed example have a look at the _examples/openapi/openapi-validator_ folder.