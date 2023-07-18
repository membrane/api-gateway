# Versioning - Versioned SOAP Endpoint Routing

This example walks you through configuring versioned endpoints in a SOAP service, with automatic routing by Membrane.


## Running the Example

1. In a terminal, set `<membrane-root>/examples/versioning/routing` as your working directory.


2. Compile and run the demo SOAP service:
    ```sh
    mvn clean compile assembly:single
    java -jar ./target/routing-maven-1.0-SNAPSHOT.jar
    ```


3. Now test if the two endpoints are reachable, in a separate terminal instance run:
    ```sh
    # Should respond with "Hello ... version 1.1"
    curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:8080/ContactService/v11
    # Should Respond with "Hello ... version 2.0"
    curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:8080/ContactService/v20
    ```
   

4. If you now send a `1.1` request to a `2.0` endpoint using the command below, you get a response containing "Cannot find dispatch method".
    ```sh
    curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:8080/ContactService/v20
    ```
   

6. Start Membrane by running `service-proxy.sh` or the `.bat` equivalent.

7. Return to the terminal, now send two requests to the single Membrane endpoint,  
    once using the v1.1 namespace URL and another using the 2.0 version:
    ```
    curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:2000/ContactService
    curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:2000/ContactService
    ```
    Observe that both requests, `1.1` and `2.0`, get a response from their respective service, although the endpoint used is the same.



## How it is done

Let's examine the `proxies.xml` file.

```xml
<router>
  <api port="2000" name="ContactService">
    <switch>
      <case xPath="//*[namespace-uri()='http://predic8.com/contactService/v11']" url="http://localhost:8080/ContactService/v11" />
      <case xPath="//*[namespace-uri()='http://predic8.com/contactService/v20']" url="http://localhost:8080/ContactService/v20"/>
    </switch>
  </api>
</router>
```

We define an `<api>` component with the endpoint context path being `ContactService`.  
Within we simply set up a `<switch>` component with two cases, each utilizing xPath to identify the namespace URL,
and proxying the requests to their respective endpoint in our demo SOAP service.

The version `1.1` of our "ContactService" uses the namespace
"http://predic8.com/contactService/v11", while version `2.0` uses
"http://predic8.com/contactService/v20".

(Note that the WSDL files reference the actual endpoint (depending on the
service version), and using them in any client (e.g. SoapUI) therefore
bypasses the proxy.)

---
See: 
- [switch](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/switch.htm) reference