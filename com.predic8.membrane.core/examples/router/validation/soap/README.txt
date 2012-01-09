SOAP Message validation

For this example to run you should install Curl from http://curl.haxx.se/download.html , if
you have not done so already. Let us assume it is in your PATH.

As the URL of a WSDL is specified in soap-validation.proxies.xml ,
the ESB retrieves all corresponding schemas and tries to validate the message body using them.

Execute the following steps:

1. Go to examples/validation/soap .

2. Start router.bat on Windows or router.sh on Linux.

3. Run the following command. Observe a successful response.

curl --header "Content-Type: application/soap+xml" -d @blz-soap.xml http://localhost:2000/axis2/services/BLZService/getBankResponse

4. Run the following command and observe that verification fails.

curl --header "Content-Type: application/soap+xml" -d @invalid-blz-soap.xml http://localhost:2000/axis2/services/BLZService/getBankResponse

