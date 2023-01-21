# Request & Response Validation against OpenAPI - Detailed Example

Membrane can validate requests and responses against OpenAPI descriptions. The specifications can be in YAML or JSON format on disk or reachable over the network.

For a basic example see the _validation-simple_ folder.


## Running the example

Make sure to use Membrane version 5 or newer.

1. Go to the _examples/openapi/validation_ directory

2. Start Membrane with the script inside this directory:

```shell
./service-proxy.sh
```

or:

```
service.proxy.bat
```

3. Send some valid and invalid requests.

**a.) From the requests.http file using Visual Studio Code or IntelliJ**

- Open _requests.http_
- Send some requests

**b.) Using curl**

Run the file _curl-requests.sh_ or parts of it.



## How it works

1. In the _proxies.xml_ configuration there is an **api** that reads the OpenAPI document and creates the API accordingly in Membrane.   

```xml
<api port="2000">
    <spec location="contacts-xxl-api-v1.yml" validateRequests="true" validateResponses="false" validationDetails="true"/>
</api>
```

2. Incomming requests are validated against the definitions in the OpenAPI specification. In case of a validation failure an error message is returned. 

3. The request is sent to the backend server with the server url from the OpenAPI definition:

```yaml
info:
  ...
servers:
  - url: http://localhost:3000
```

4. The answer is returned to the client.

5. You can also switch on response validation and check that the answer from the server is valid too.

```
validateResponses="true"
```
