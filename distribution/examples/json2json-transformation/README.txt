JSON 2 JSON TRANSFORMATION

Transforms a JSON object into another JSON object with help of Javascript.

RUNNING THE EXAMPLE

In this example we will use Javascript to transform a JSON object response into another JSON object. For the date transformation an external library ( "moment.js" ) is used.

Execute the following steps in the $MEMBRANE_HOME/examples/json2json-transformation folder:

1. take note that the data is acquired from "api.predic8.de/shop/orders/7958" through a request

2. take a look at converter.js. This script transforms the data

3. run service-proxy.sh/.bat

4. open "curl localhost:2000/shop/orders/7958" in the command line

5. see the transformed JSON object

NOTES

To see the Javascript implementation of this example take a look at the proxies.xml.

The underlying Javascript engine is Nashorn. It implements ECMAScript 5.1 and further extensions for interaction with Java code.

For further reference of Nashorn see https://docs.oracle.com/javase/8/docs/technotes/guides/scripting/prog_guide/toc.html.



