JAVASCRIPT

The Javascript interceptor runs Javascript scripts to manipulate and monitor messages.

RUNNING THE EXAMPLE

In this example we will use Javascript to manipulate a request and monitor the response of it. We will also use Javascript to serve static responses for requests.

Execute the following steps:

1. download cURL from https://curl.haxx.se/download.html and install it

2. run "curl localhost:2000" in the command line

3. take a look at the membrane console window. It lists the http headers from the request response

4. run "curl localhost:2001" in the command line

5. see a custom response from the Javascript interceptor

NOTES

To see the Javascript implementation of this example take a look at the proxies.xml.

The underlying Javascript engine is Nashorn. It implements ECMAScript 5.1 and further extensions for interaction with Java code.

For further reference of Nashorn see https://docs.oracle.com/javase/8/docs/technotes/guides/scripting/prog_guide/toc.html.



