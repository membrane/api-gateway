# if Plugin

Control the execution of plugins with `if` conditionally. The SpEL expression language, Groovy, Jsonpath and XPath can be used to write the condition. 

## Running the Example

***Note:*** *You can test these requests using the provided HTTP request snippets.*

1. **Navigate** to the `examples/extending-membrane/if` directory.
2. **Start** the Router by executing `router-service.sh` (Linux/Mac) or `router-service.bat` (Windows).
3. **Execute the following requests** (alternatively, use the `requests.http` file):

  - **JSON Request**:
    ```bash
    curl -X POST http://localhost:2000 -H "Content-Type: application/json" -d '{"foo": "bar"}' -v
    ```
  - **JSON with 'name' Key as 'foo'**:
    ```bash
    curl -X POST http://localhost:2000 -H "Content-Type: application/json" -d '{"name": "foo"}' -v
    ```
  - **Query Parameter Check**:
    ```bash
    curl -X GET 'http://localhost:2000/?param1=value1' -v
    ```
    ```bash
    curl -X GET 'http://localhost:2000/?param1=value2' -v
    ```
  - **Header Check for 'bar'**:
    ```bash
    curl -X GET http://localhost:2000 -H "X-Test-Header: foo" -v
    ```
    ```bash
    curl -X GET http://localhost:2000 -H "X-Test-Header: foobar" -v
    ```
  - **Large Body Detection**:
    ```bash
    curl -X POST http://localhost:2000 -d "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum." -v
    ```

4. **Review the Configuration**:
  - Take a look at `proxies.xml` to understand the configuration details and the conditional logic applied.


## Response Manipulation

A common use case of the `if` plugin is the manipulation of responses based on the status code:

```xml
    <response>
        <if test="statusCode matches '[45]\d\d'">
            <template pretty="yes" contentType="application/json">
                {
                  "type": "https://membrane-api.io/error/",
                  "title": "${exc.response.statusMessage}"
                }
            </template>
        </if>
        <if test="statusCode == 302" language="SpEL">
            <log message="Status code changed!"/>
            <groovy>exc.getResponse().setStatusCode(404)</groovy>
        </if>
    </response>

    <template>Success</template>
    <return statusCode="302"/>
</api>
```

`if` allows you to dynamically handle requests and adjust responses based on specified conditions.