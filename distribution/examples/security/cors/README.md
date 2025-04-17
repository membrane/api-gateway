# Cross-Origin Resource Sharing

The usage of APIs in web sides is restricted by the Cross-Origin restrictions of modern Web browsers. To allow calling APIs even with POST, HTTP headers or even credentials the addition of special CORS headers is needed. 

The cors plugin makes it easy to add those headers and to answer preflight OPTIONS requests made by the browser. 

For an in-depth explanation of using CORS for APIs, check out the [CORS Guide for API Developers](https://www.membrane-api.io/cors-api-gateway.html).

1. Set up CORS with Membrane

```xml
<api port="2000">
    <cors origins="http://localhost:2001" methods="GET, POST"/>
    <target host="localhost" port="3000"/>
</api>
```

This configuration defines an API named ```cors``` running on port ```2000```.
CORS is enabled, allowing requests from ```http://localhost:2001``` with ```GET``` and ```POST``` methods.
Incoming requests are forwarded to a target service running on ```localhost:3000```.

2. Set up Static Server

```xml
<api port="3000">
    <static>foo</static>
    <return statusCode="210"/>
</api>
```

Serves static content from the ```foo``` directory and returns status code ```210```.

3. Set up test.html

```xml
<api name="html" port="2001">
    <webServer docBase="."/>
</api>
```

Serves the HTML test page via a simple web server.

4. How to test

    1. Open [http://localhost:2001/test.html](http://localhost:2001/test.html) in your browser.
    2. Open the browser’s developer tools → Network tab.
    3. Click the **"Call API"** button.
    4. You should see a POST request sent to ```http://localhost:2000```, forwarded to port ```3000```, and a ```210```
       response returned.
    5. The text "foo" should be clearly shown in the text field.
    6. CORS headers should be visible in the response.

For all configuration options see [CORS](https://www.membrane-api.io/docs/current/cors.html) at the Membrane reference.