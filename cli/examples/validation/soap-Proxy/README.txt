SOAP Message Validation

To run this example execute the following steps

PREREQUISITES:

- Install Curl from http://curl.haxx.se/download.html , if you have not done so already. Let us assume it is in your PATH.

As the URL of a WSDL is specified in proxies.xml, the ESB retrieves all corresponding schemas and tries to validate the message body using them.

As a SOAP-Proxy is a specialized version of the ServiceProxy, a SOAP-Proxy has a certain advantage if it is used for a SOAP Service. A SOAP-Proxy needs the WSDL's URL just once. Adding a Validator then needs less code, as you can see in the example below:

<soapProxy port="2000" wsdl="http://www.thomas-bayer.com/axis2/services/BLZService?wsdl">
	<validator />
</soapProxy>


Execute the following steps:

1. Go to examples/validation/soap-Proxy

2. Start service-proxy.bat or service-proxy.sh

3. Navigate into the soap-Proxy folder and run the following command on the console. Observe a successful response.

curl --header "Content-Type: application/soap+xml" -d @blz-soap.xml http://localhost:2000/axis2/services/BLZService/getBankResponse

4. Run the following command in the same directory and observe that verification fails.

curl --header "Content-Type: application/soap+xml" -d @invalid-blz-soap.xml http://localhost:2000/axis2/services/BLZService/getBankResponse

