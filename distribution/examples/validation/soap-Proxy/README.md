# Validation - SOAP

This sample explains how to set up and use the `validator` plugin within a `soapProxy` component.


## Running the Example

1. Go to `<membrane-root>/examples/validation/soap-Proxy`

2. Start `service-proxy.bat` or `service-proxy.sh`

3. Navigate into the `soap-Proxy` folder and run the following command on the console. Observe a successful response.

```
curl --header "Content-Type: application/soap+xml" -d @blz-soap.xml http://localhost:2000/axis2/services/BLZService/getBankResponse
```
4. Run the following command in the same directory and observe that verification fails.
```
curl --header "Content-Type: application/soap+xml" -d @invalid-blz-soap.xml http://localhost:2000/axis2/services/BLZService/getBankResponse
```

---
See:
- [validator](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/validator.htm) reference