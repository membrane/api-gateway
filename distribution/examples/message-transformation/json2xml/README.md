# Json2Xml Message Transformation

The `json2xml` plugin converts JSON content to XML documents.


## Running the Sample

To run the example, execute the following steps:

1. Go to the examples/message-transformation/json2xml directory

2. Run `service-proxy.sh` or `service-proxy.bat` to start the API Gateway.

3. Run the command below in a second console from the same directory:

 ```
curl -d @customers.json http://localhost:2000 -H "Content-Type: application/json"
 ```

4. You can see the converted XML in the console


## How it works

Let's take a look at the `proxies.xml` file.

```
<api port="2000">
  <request>
    <json2Xml/>
  </request>
  <target url="http://localhost:3000"/>
</api>
 ```

In the `proxies.xml` file you can see the `json2Xml` plugin. The plugin is placed inside the `request` so it only affects requests. You can also put it inside `response`.

The `xml2Json` plugin will intercept messages based on the content type, and it changes the content type to `text/xml`.

---
See:
- [json2Xml](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/json2Xml.htm) reference