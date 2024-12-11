# Validation - SOAP

This sample explains how to set up and use the `validator` plugin within a `soapProxy` component.


## Running the Example

1. Go to `<membrane-root>/examples/validation/soap-Proxy`


2. Start `service-proxy.bat` or `service-proxy.sh`


3. Run the following command, observe a successful response:  
    `curl -H "Content-Type: application/soap+xml" -d @city-soap.xml http://localhost:2000/`


4. Run this next command and observe that verification fails:  
    `curl -H "Content-Type: application/soap+xml" -d @invalid-city-soap.xml http://localhost:2000/`

## How it is done

Let's examine the `proxies.xml` file.

```xml
<router>
    <soapProxy port="2000" wsdl="city-service.wsdl">
    <validator />
  </soapProxy>
</router>
```

We define a `<soapProxy>` component running on port 2000, which is an expanded version of the basic `<api>` and `<serviceProxy>` components, capable of storing a WSDL manifest.
Now we simply put a `<validator />` component withing the `<soapProxy>`, without any attributes, as it inherits the WSDL from the proxy component.

---
See:
- [validator](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/validator.htm) reference