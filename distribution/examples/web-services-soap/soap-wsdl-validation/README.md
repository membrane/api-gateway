# SOAP Message Validation using WSDL and XSD

The `validator` plugin can validate SOAP request and response messages against WSDL descriptions.


## Running the Example

1. Go to `<membrane-root>/examples/web-services-soap/soap-wsdl-validation`

2. Start `membrane.cmd` or `membrane.sh`

3. Run the following command, observe a successful response:  

   ```bash
   curl -H "Content-Type: application/xml" -d @city-soap.xml http://localhost:2000/
   ```

4. Run this next command and observe that verification fails:  
   ```bash
   curl -H "Content-Type: application/xml" -d @invalid-city-soap.xml http://localhost:2000/
   ```

See: `apis.yaml`