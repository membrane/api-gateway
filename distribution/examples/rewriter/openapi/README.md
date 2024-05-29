To improve this documentation without adding too much text, we can focus on enhancing clarity, organization, and conciseness. Here is the revised version:

---

# OpenAPI Rewriter

Using the rewriter in the [OpenAPI](../../openapi) element, you can rewrite the host, port, or path of an OpenAPI.

### Running the Example

1. Go to the `examples/rewriter/openapi` directory.

2. Take a look at the `demo-api-b1.yml`:
    ```yaml
    servers:
    - url: http://localhost:2000/demo-api/v1/
    ```
   - Base path: `/demo-api/v1/`
   - Port: `2000`
   - Host: `localhost`

3. Execute `service-proxy.sh` or `service-proxy.bat`.
4. Review the [proxies.xml](./proxies.xml) configuration.
    <br></br>
    **Rewriting the Path**:
    
    For port 2000:
    ```xml
    <api port="2000">
        <openapi location="./demo-api-v1.yml" validateRequests="yes">
            <rewrite url="/bar" />
        </openapi>
    </api>
    ```
    - Open [localhost:2000/api-docs](http://localhost:2000/api-docs) and click on `Demo API`.
    - The base path is now `/bar` instead of `/demo-api/v1/`.
    <br></br>
      
    **Rewriting the Port**:
    
    For port 2001:
    ```xml
    <api port="2001">
        <openapi location="./demo-api-v1.yml" validateRequests="yes">
            <rewrite port="3000" />
        </openapi>
    </api>
    ```
    - Open [localhost:2001/api-docs](http://localhost:2001/api-docs) and click on `Demo API`.
    - The port is now `3000` instead of `2000`.
    <br></br>
      
    **Rewriting the Host**:
    
    For port 2002:
    ```xml
    <api port="2002">
        <openapi location="demo-api-v1.yml" validateRequests="yes">
            <rewrite host="predic8.de" />
        </openapi>
    </api>
    ```
    - Open [localhost:2002/api-docs](http://localhost:2002/api-docs) and click on `Demo API`.
    - The host is now `predic8.de` instead of `localhost`.
    <br></br>
      
    **Combining Rewrites**:
    
    Rewriting host, port, and path:
    ```xml
    <api port="2003">
        <openapi location="demo-api-v1.yml" validateRequests="yes">
            <rewrite host="predic8.de" port="3000" url="/foo"/>
        </openapi>
    </api>
    ```
    - This changes the original `localhost:2000/demo-api/v1/` to `predic8.de:3000/foo`.

---

For more details, see the [rewriter](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/rewriter.htm) reference.
