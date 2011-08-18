REST 2 SOAP INTERCEPTOR

With the REST2SOAPInterceptor you can make a SOAP Web Service as a REST resource accessible. In contrast to the rest2soap example here the response will be a JSON object. 




RUNNING THE EXAMPLE

In this example we will call a SOAP Web Service by using a simple HTTP GET request. We will use a service that identifies the name, zip code and region of a bank by its banking code. You can take a look at the WSDL at 


http://www.thomas-bayer.com/axis2/services/BLZService?wsdl



To run the example execute the following steps:

1. Execute router.bat.

3. Open the URL http://localhost:2000/bank/37050198 in your browser.

4. Try it again with a different banking code e.g. http://localhost:2000/bank/66762332.