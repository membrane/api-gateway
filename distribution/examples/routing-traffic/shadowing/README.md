# Shadowing Interceptor

This example demonstrates how to send requests to multiple shadow hosts. A request is sent to the primary target, with additional requests concurrently sent to shadow hosts. The response from the primary target is returned immediately, while shadow requests are processed in the background.

## Running the Example

1. Run `membrane.cmd` or `membrane.sh`
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

## Configuration
The targets specified in `shadowing` are shadow hosts. Responses from these hosts are ignored; however, if the returned status code is a 5XX, the endpoint that generated this response is logged.
```xml
<api port="2000">
    <shadowing>
        <target host="localhost" port="3000" />
        <target host="localhost" port="3001" />
        <target host="localhost" port="3002" />
    </shadowing>
    <target host="api.predic8.de" port="443">
        <ssl/>
    </target>
</api>
```