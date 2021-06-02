### json2Xml INTERCEPTOR

Using `json2xml` interceptor you can convert JSON content to XML in both requests and responses.


#### RUNNING THE EXAMPLE

To run the example, execute the following steps:

1. Go to the examples/json-2-xml directory

2. Execute service-proxy.sh

3. Run below command in another console

 ```
curl -d @customers.json http://localhost:2000 -H "Content-Type: application/json"
 ```

4. You can see the converted xml content on the console


#### HOW IT IS DONE

The following part describes the example in detail.

Let's take a look at the proxies.xml file.


```
<serviceProxy name="echo" port="2000">
    <log headerOnly="false"/>
    <request>
        <json2Xml/>
    </request>
    <groovy>
        Response.ok("Look at the output of Membrane!\n").build()
    </groovy>
</serviceProxy>
 ```

In the proxies.xml file you can see the `json2Xml` interceptor. Since we put our interceptor in between request tags it only
works for the requests. You can also put it inside response tags and make it work in response also.

This is possible because `json2xml` interceptor can work both ways.

xml2Json interceptor will intercept message based on the content type, and it will use `XML.toString` from org.json for conversion.
It will also change content type of message to `text/xml`.

