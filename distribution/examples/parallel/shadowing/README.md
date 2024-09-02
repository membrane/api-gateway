# Traffic Shadowing

This example demonstrates the use of `the shadowing strategy` to send requests to multiple shadow hosts. A request is sent to the primary target, with additional requests concurrently sent to shadow hosts. The response from the primary target is returned immediately, while shadow requests are processed in the background.

## Running the Example

1. Run `service-proxy.bat` or `service-proxy.sh`
2. Open [localhost:2000](http://localhost:2000) in your browser or use `curl`:

      ```                                                                                                    
      curl -v http://localhost:2000
      ```

   The output should look like this:
   
   ```json
   {
      "apis": [
        {
          "name": "Shop API Showcase",
          "description":"API for REST exploration, test and demonstration. Feel free to manipulate the resources using the POST, PUT and DELETE methods. This API acts as a showcase for REST API design.",
          "url":"/shop/v2/"
        }
      ]
   }
   ```
   
   When `logShadowResponse` is set to `true`, the console will also log the response from the shadow host.
   ```
   [Thread-2] INFO com.predic8.membrane.core.interceptor.parallel.strategies.ShadowingStrategy - Response from http://localhost:3000: HTTP/1.1 200 Ok
   Content-Type: text/plain
   Content-Length: 72
   ```
3. Additionally, you can modify the `returnTarget` in `proxies.xml` and set it to `secondary`, which corresponds to the shadow host’s ID. This switch makes the shadow host the new primary target. When you repeat the request to [localhost:2000](http://localhost:2000), you'll notice that the response now takes about 5 seconds, while the shadow host's response is logged almost immediately.

## Configuration

Just declare the returnTarget of the desired endpoint. You can log the responses of the shadow hosts by setting `logShadowResponse` to `true`. 

```xml
<api port="2000">
   <parallel>
      <shadowing returnTarget="primary" logShadowResponse="true"/>
      <target url="https://api.predic8.de" id="primary" />
      <target url="http://localhost:3000" id="secondary" />
   </parallel>
</api>
```