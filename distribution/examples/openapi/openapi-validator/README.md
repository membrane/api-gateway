# Request & Response Validation against OpenAPI - Detailed Example

The **OpenAPIProxy** can validate requests and responses against OpenAPI specifications. The specifications can be in YAML or JSON format on disk or reachable over the network.

For a simpler example see the _openapi-validator-simple_ folder.


## RUNNING THE EXAMPLE

1. Go to the _examples/openapi/openapi-validator_ directory

2. Start Membrane with the script inside this directory:

```
./service-proxy.sh
```

or:

```
service.proxy.bat
```

3. Send some valid and invalid requests from the following list. You can use curl oder habe a look at the _requests.http_ file.

Valid request, should work => 200 Ok

```
curl http://localhost:2000/demo-api/v2/persons?limit=10
```

Wrong path => 404 Not Found

```
curl http://localhost:2000/demo-api/v2/wrong
```

Limit greater than 100 => 400 Bad Request

```
curl http://localhost:2000/demo-api/v2/persons?limit=200
```

Valid => 200 Ok

```
curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 -H'content-type: application/json' -d '{"name": "Jan Vermeer"}'
```

Invalid UUID, email and enum => 400 Bad Request

```
curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2+DDFC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","email": "jan(at)schilderei.nl","type": "ARTIST"}'
```

3. In the file proxies.xml change the attribute _validate_ from _requests_ to _all_ and save it.

```
<OpenAPIProxy port="2000">
    <spec location="contacts-api-v1.yml" validate="all"/>
</OpenAPIProxy>
```

4. Stop Membrane by press *CRTL-C* in the console and restart it again:

```
./service-proxy.sh
```

5. 


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