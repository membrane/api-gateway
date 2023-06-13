### Xml2json INTERCEPTOR

Using `xml2json` interceptor you can convert XML content to JSON in both requests and responses.


#### RUNNING THE EXAMPLE

To run the example execute the following steps: 

1. Go to the examples/xml-2-json directory.

2. Execute service-proxy.sh

3. Run below command in another console

 ```
curl -d @jobs.xml http://localhost:2000 -H "Content-Type: application/xml"
 ```

4. You can see converted json content on the console


#### HOW IT IS DONE

The following part describes the example in detail.  

Let's take a look at the proxies.xml file.

```
<router>
  <api port="2000">
    <request>
      <xml2Json/>
    </request>
  </api>
</router>
 ```

In the proxies.xml file you can see the `xml2Json` interceptor. Since we put our interceptor in between request tags it only
works for the requests. You can also put it inside response tags and make it work in response also.

This is possible because `xml2Json` interceptor can work both ways.

xml2Json interceptor will intercept message based on the content type, and it will use `XML.toJSONObject` from org.json for conversion.
It will also change content type of message to `application/json;charset=utf-8`.

---
See:
- [xml2Json](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/xml2Json.htm) reference