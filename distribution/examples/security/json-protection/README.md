# Protecting against JSON attacks - JSON Protection Example

**Since Membrane 5.2.0**

Membrane can identify misuse in JSON documents such as duplicate fields, unusually large arrays or strings, and excessively nested documents. This allows Membrane to prevent potentially harmful JSON from compromising API backends.

## Running the Example

1. Start Membrane using the included script: 
 
    **Linux:**
    ```shell
    cd examples/security/json-protection
    ./service-proxy.sh
    ```

    **Windows**:
    ```shell
    cd examples/security/json-protection
    service-proxy.bat
    ```

2. Send a message violating the configuration. Depth is set to a maximum of 3.

    ```shell
    curl -d '{"a": {"b": {"c": {"d": 1}}}}' -v localhost:2000
    ```

    The API Gateway should answer with a `400 Bad Request`.

3. Have a look at the configuration in the `proxies.xml` file.

    ```xml
    <jsonProtection maxTokens="15" 
                    maxSize="110" 
                    maxDepth="3"
                    maxStringLength="5"
                    maxKeyLength="1"
                    maxObjectSize="3"
                    maxArraySize="3" />
    ```

## More Examples

Look at the examples in the provided script:

**Linux:**
```shell
./requests.sh
```

**Windows:**
```batch
requests.bat
```

Or run individual requests from the `requests.http` file using editors or IDEs supporting `.http` files( Intellj, Visual Studio Code).


The requests will test several cases of malicious JSON. Take a look at the `proxies.xml` file to get an idea of how to set up the plugin.  

**See**:  
* [jsonProtection](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/jsonProtection.htm) reference
