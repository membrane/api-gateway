# Replace Interceptor

The `Replace`plugin allows you to modify values in your JSON by using a `jsonPath` expression to target specific fields for replacement.
## Running the Example

1. Run `service-proxy.bat` or `service-proxy.sh`
2. Send a request using `curl`:

   ```shell
    curl -X POST localhost:2000 \
    -H "Content-Type: application/json" \
    -d '{"shop": {"name": "MyShop", "location": "Berlin"}}'
    ```
3. Check the console, and you'll notice that the JSON with the name field set to `foo` is logged.
    ```
    [RouterThread /127.0.0.1:33632] INFO com.predic8.membrane.core.interceptor.LogInterceptor - Body:
    [RouterThread /127.0.0.1:33632] INFO com.predic8.membrane.core.interceptor.LogInterceptor - {"shop":{"name":"foo","location":"Berlin"}}
    [RouterThread /127.0.0.1:33632] INFO com.predic8.membrane.core.interceptor.LogInterceptor - ================
    [RouterThread /127.0.0.1:33632] INFO com.predic8.membrane.core.interceptor.LogInterceptor - ==== Response ===
    [RouterThread /127.0.0.1:33632] INFO com.predic8.membrane.core.interceptor.LogInterceptor - HTTP/1.1 200 Ok
    ```

## Configuration

This configuration sets up an API that replaces the value of the `name` field under the `shop object` in the JSON body with "foo", before forwarding the request to a target service running on localhost at port 3000.
```xml
<api port="2000">
  <replace jsonPath="$.shop.name" with="foo" />
  <target host="localhost" port="3000" />
</api>
```