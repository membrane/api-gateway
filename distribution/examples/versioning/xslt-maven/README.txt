VERSIONING WEBSERVICES BY PERFORMING XSLT

In this example we investigate how web services can be versioned by transforming
old requests using XSLT.

Membrane acts as a common proxy and routes SOAP requests to the new service
endpoint, depending on the XML namespace used in the SOAP message body. Old
requests are transformed into new ones using an XSLT stylesheet.

The version 1.1 of our "ContactService" used the namespace
"http://predic8.com/contactService/v11", while version 2.0 uses
"http://predic8.com/contactService/v20".


(Note that the WSDL file references the actual endpoint (depending on the
service version), and using them in any client (e.g. SoapUI) therefore
bypasses the proxy.)


RUNNING THE EXAMPLE

To run the example execute the following steps: 

Execute the following steps:

1. To test the router we will use the command line tool curl that can transfer
   data with URL syntax. You can download it form the following location:
     
   http://curl.haxx.se/download.html

2. Open a new console window and execute:
     cd [MEMBRANE_HOME]\examples\versioning\xslt-maven
     mvn clean compile assembly:single
     java -jar ./target/xslt-maven-1.0-SNAPSHOT.jar

3. Open yet another console window and run
     cd [MEMBRANE_HOME]\examples\versioning\xslt-maven
     curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:9000/ContactService/v11
   You should see a response containing "<h1>404 Not Found</h1>", "No context found for request".

4. Now run
     curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:9000/ContactService/v20
   You should see a response containing "Hello ... version 2.0".

5. If you now send a 1.1 response to a 2.0 endpoint, you get
     curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:9000/ContactService/v20
   a response containing "Cannot find dispatch method".


6. We now start Membrane: Execute examples/versioning/xslt-maven/service-proxy.bat .

7. Return to the console. Run both:
     curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:2000/ContactService
     curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:2000/ContactService

  Observe that both requests, 1.1 and 2.0, get a valid response from the service, although
  the first one uses an old request format.


