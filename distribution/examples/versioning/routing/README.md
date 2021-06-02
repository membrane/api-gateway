### VERSIONING WEBSERVICES BY ROUTING TO DIFFERENT ENDPOINTS

In this example we investigate how web services can be versioned by running
each version on its own seperate end point.

Membrane acts as a common proxy and routes SOAP requests to the service endpoint,
depending on the XML namespace used in the SOAP message body.

The version `1.1` of our "ContactService" uses the namespace
"http://predic8.com/contactService/v11", while version `2.0` uses
"http://predic8.com/contactService/v20".


(Note that the WSDL files reference the actual endpoint (depending on the
service version), and using them in any client (e.g. SoapUI) therefore
bypasses the proxy.)


#### RUNNING THE EXAMPLE

To run the example execute the following steps: 

Execute the following steps:

1. To test the router we will use the command line tool curl that can transfer
   data with URL syntax. You can download it from the following location:
     
   http://curl.haxx.se/download.html

2. Open a new console window and execute:
```
cd [MEMBRANE_HOME]\examples\versioning\routing-maven
mvn clean compile assembly:single
java -jar ./target/routing-maven-1.0-SNAPSHOT.jar
```
3. Open yet another console window and run
```
cd versioning\routing
curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:8080/ContactService/v11
```
You should see a response containing "Hello ... version 1.1".

4. Now run below command. You should see a response containing "Hello ... version 2.0".
```
    curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:8080/ContactService/v20
```  
   

5. If you now send a `1.1` response to a `2.0` endpoint using command below, you get a response containing "Cannot find dispatch method".
```
 curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:8080/ContactService/v20
```


6. We now start Membrane: Execute `examples/versioning/routing/service-proxy.bat` .

7. Return to the console. Run both:
```
 curl --header "Content-Type: text/xml" -d @request_v11.xml http://localhost:2000/ContactService
 curl --header "Content-Type: text/xml" -d @request_v20.xml http://localhost:2000/ContactService
```
  Observe that both requests, `1.1` and `2.0`, get a response from their respective service, although
  the endpoint used is the same.


