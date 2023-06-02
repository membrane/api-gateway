### REST 2 SOAP CONVERSION

With the `REST2SOAP` converter you can make a SOAP Web Service accessible as a REST resource. In contrast to the rest2soap example here the response will be a JSON object. 




#### RUNNING THE EXAMPLE

In this example we will call a SOAP Web Service by using a simple HTTP GET request. We will use a service that identifies the name, zip code and region of a bank by its banking code. You can take a look at the WSDL at 


http://www.thomas-bayer.com/axis2/services/BLZService?wsdl

To test the router we will use the command line tool curl that can transfer data with URL syntax. You can download it from the following location:
   
     
   http://curl.haxx.se/download.html


To run the example execute the following steps:

1. Execute `service-proxy.bat` or `service-proxy.sh`.

2. Open a new console and execute `curl --header "Accept: application/json" http://localhost:2000/bank/37050198`

4. Try it again with a different banking code e.g. `curl --header "Accept: application/json" http://localhost:2000/bank/66762332`

--- 
See:
- [rest2Soap](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/rest2Soap.htm) reference