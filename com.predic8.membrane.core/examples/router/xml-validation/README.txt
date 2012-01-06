XML Validation

For this example to run you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already. Let us assume it is in your PATH.

Execute the following steps:

1. Start "router.bat" in examples/xml-validation.

2. Go to the directory examples/xml-validation.

3. Run "curl -d @year.xml http://localhost:2000/". Observe that you get a successful response.

4. Run "curl -d @invalid-year.xml http://localhost:2000/". Observe that you get a validation error response.



Now for SOAP Message validation. As the URL of a WSDL is specified in xml-validation.proxies.xml ,
the ESB retrieves all corresponding schemas and tries to validate the message body using them.

5. Run the following command. Observe a successful response.

curl --header "Content-Type: application/soap+xml" -d @blz-soap.xml http://localhost:2001/axis2/services/BLZService/getBankResponse

6. Run the following command and observe that verification fails.

curl --header "Content-Type: application/soap+xml" -d @invalid-blz-soap.xml http://localhost:2001/axis2/services/BLZService/getBankResponse

