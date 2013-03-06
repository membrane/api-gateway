VERSIONING WEBSERVICES BY ROUTING TO DIFFERENT ENDPOINTS

In this example we investigate how web services can be versioned by running
each version on its own seperate end point.

Membrane acts as a common proxy and routes SOAP requests to the service endpoint,
depending on the XML namespace used in the SOAP message body.

The version 1.1 of our "ContactService" uses the namespace
"http://predic8.com/contactService/v11", while version 2.0 uses
"http://predic8.com/contactService/v20".


(Note that the WSDL files reference the actual endpoint (depending on the
service version), and using them in any client (e.g. SoapUI) therefore
bypasses the proxy.)


RUNNING THE EXAMPLE

To run the example execute the following steps: 

Execute the following steps:

1. To test the router we will use the command line tool curl that can transfer
   data with URL syntax. You can download it form the following location:
     
   http://curl.haxx.se/download.html

   Download Apache Ant from http://ant.apache.org/bindownload.cgi and unpack it
   (Let us say to C:\work\apache-ant-1.8.2-bin . Let us also say your Java
   resides in C:\Program Files\Java\jdk1.7.0_01 .) 

   Execute the following commands:
     set JAVA_HOME=C:\Program Files\Java\jdk1.7.0_01
     set PATH=%PATH%;C:\work\apache-ant-1.8.2-bin\bin

2. Open a new console window and execute:
     cd [MEMBRANE_HOME]\examples\versioning\routing
     ant run

3. Open yet another console window and run
     cd versioning\routing
     curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:8080/ContactService/v11
   You should see a response containing "Hello ... version 1.1".

4. Now run
     curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:8080/ContactService/v20
   You should see a response containing "Hello ... version 2.0".

5. If you now send a 1.1 response to a 2.0 endpoint, you get
     curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:8080/ContactService/v20
   a response containing "Cannot find dispatch method".


6. We now start Membrane: Execute examples/versioning/routing/router.bat .

7. Return to the console. Run both:
     curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:2000/ContactService
     curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:2000/ContactService

  Observe that both requests, 1.1 and 2.0, get a response from their respective service, although
  the endpoint used is the same.


