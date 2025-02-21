# Versioning - SOAP Request and Response Version Transformation

This example demonstrates how one service endpoint can seamlessly support multiple versions of SOAP requests and responses using `XSLT` transformations in Membrane API Gateway.

## Overview
In many enterprise environments, services evolve over time, resulting in different clients using different versions of the same SOAP API. To maintain backward compatibility without deploying separate endpoints, Membrane API Gateway can transform incoming and outgoing messages dynamically, ensuring uniform service availability across versions.

## Running the Example

1. **Navigate to the Example Directory**
   Open a terminal and set the working directory to:
   ```sh
   cd <membrane-root>/examples/web-services-soap/versioning-soap-xslt
   ```

2. **Start Membrane**
   Run the following command based on your operating system:
   ```sh
   # For Unix/Mac
   ./membrane.sh
   
   # For Windows
   membrane.cmd
   ```

3. **Send a SOAP Request (New Version)**
   Open a new terminal and test the service by sending a SOAP request that uses the new version:
   ```sh
   curl -H "Content-Type: text/xml" -d @request-new.xml http://localhost:2000/city-service
   ```
   
   The response should provide information about the city of Bonn.

4. **Send a SOAP Request (Old Version)**
   The service should also accept and transform older SOAP request versions:
   ```sh
   curl -H "Content-Type: text/xml" -d @request-old.xml http://localhost:2000/city-service
   ```

## How it Works
Membrane leverages `XSLT` transformations to convert incoming and outgoing SOAP messages dynamically. This ensures compatibility with both old and new clients without modifying the core service logic.

### Key Configuration - `proxies.xml`
The following configuration (found in `proxies.xml`) shows how to handle version transformations:

```xml
<!-- Endpoint that accepts requests from old and new clients -->
<api port="2000">
    <request>
        <!-- Convert old namespace requests to the new version -->
        <if test="//*[namespace-uri() = 'https://predic8.de/old']" language="XPath">
            <transform xslt="convert-request-to-new-version.xslt"/>
            <setProperty name="converted" value="true"/>
        </if>
    </request>
    
    <response>
        <!-- Transform response body back to the old version if the request was converted -->
        <if test="properties['converted'] == 'true'">
            <transform xslt="convert-response-to-old-version.xslt"/>
        </if>
    </response>

    <!-- Implementation of the SOAP service for the new version -->
    <sampleSoapService/>
</api>
```

### Explanation
- **Request Transformation:** If an incoming SOAP request uses an old namespace (`https://predic8.de/old`), Membrane applies `convert-request-to-new-version.xslt` to transform it to the new format.
- **Response Transformation:** After processing, if the request was converted, the response is transformed back to the old format using `convert-response-to-old-version.xslt`.
- **Seamless Integration:** The service implementation (`sampleSoapService`) remains unchanged, ensuring minimal disruption while supporting multiple client versions.

## XSLT Transformation Files
- **`convert-request-to-new-version.xslt`**: Converts old SOAP requests to the new version.
- **`convert-response-to-old-version.xslt`**: Converts responses from the new version back to the old format.

## References
- [soapProxy Configuration Reference](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/soapProxy.htm)
- [switch Configuration Reference](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/switch.htm)