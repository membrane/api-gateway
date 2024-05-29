# OpenAPI Base URL Rewriter

Using the rewriter in the [OpenAPI](../../openapi) element, you can rewrite the host, port, or path of an OpenAPI.

### Running the Example

1. Go to the `examples/rewriter/openapi` directory.

2. Take a look at the base URL in the `demo-api-v1.yml`:
    ```yaml
    servers:
    - url: http://localhost:2000/demo-api/v1/
    ```
3. Execute `service-proxy.sh` or `service-proxy.bat`.
4. Review the [proxies.xml](./proxies.xml) configuration. 
    ```xml
    <api port="2000">
        <openapi location="demo-api-v1.yml" validateRequests="yes">
            <rewrite host="predic8.de" port="3000" basePath="/foo"/>
        </openapi>
    </api>
    ```
    - Open [localhost:2000/api-docs](http://localhost:2000/api-docs) and click on `Demo API`.
    - The base path of the OpenAPI will be rewritten to `/foo`.
    - The host will be rewritten to `predic8.de`.
    - The port will be rewritten to `3000`.
