# Versioning - SOAP Message Version Transformation

This sample demonstrates how to rewrite SOAP message versions using `XSLT`.


## Running the Example

1. In a terminal, set `<membrane-root>/examples/versioning/xslt` as your working directory.


2. Compile and run the demo SOAP service:
    ```sh
    mvn clean compile assembly:single
    java -jar ./target/xslt-maven-1.0-SNAPSHOT.jar
    ```


3. Now test if the endpoint is reachable, in a separate terminal instance run:
    ```sh
   # Should Respond with "Hello ... version 2.0"
    curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:9000/ContactService/v20
    ```
   
    Now run the same command but sending a message using `v1.1` instead of `v2.0`:
    ```sh
    curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:9000/ContactService/v20
    ```
    You should see a response containing `<h1>404 Not Found</h1>`, `No context found for request`.

    Lastly try accessing a v1.1 endpoint with a similar command:
    ```sh
    curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:9000/ContactService/v11
    ```
    No endpoint for this version is available.


4. Start Membrane by running `service-proxy.sh` or the `.bat` equivalent.


5. Return to another terminal, send two requests, one for versions `1.1` and `2.0` respectively, 
    to the single Membrane endpoint:
    ```sh
    curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:2000/ContactService
    curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:2000/ContactService
    ```
   Observe that both requests, `1.1` and `2.0`, get a valid response from the service, although 
    the first one uses an old request format.

## How it is done

Let's examine the `proxies.xml` file.

```xml
<router>
  <soapProxy wsdl="http://localhost:8080/ContactService/v20?wsdl" port="2000">
    <path>/ContactService</path>
    <switch>
      <case xPath="//*[contains(namespace-uri(), '/contactService/v11')]"
                  service="v11-to-v20" />
    </switch>
  </soapProxy>
    
  <soapProxy wsdl="http://localhost:8080/ContactService/v20?wsdl" name="v11-to-v20" port="2000">
    <path>/ContactService</path>
    <request>
      <transform xslt="conf/v11-to-v20.xslt"/>
    </request>
  </soapProxy>
</router>
```

We define two `<soapProxy>` components and specify our single `v2.0` service/WSDL address.  
When sending a SOAP message to the endpoint context path `ContactService`,
a `<switch>` component uses xPath to determine if the message is using an old version of the format.
If this is the case we proxy the request to the second `<soapProxy>`, where a `<transform>` plugin is applied to every request.  
Old requests are transformed into new ones using an XSLT stylesheet, specified within the `xslt` attribute of the `transform`.

---
See:
- [soapProxy](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/soapProxy.htm) reference
- [switch](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/switch.htm) reference