# Replace Plugin

The `Replace`plugin allows you to modify values in your JSON by using a `jsonPath` expression to target specific fields for replacement.
## Running the Example

1. Run `service-proxy.ps1` or `service-proxy.sh`
2. Send a request using `curl`:

   ```shell
    curl localhost:2000 \
    -H "Content-Type: application/json" \
    -d '{"user": {"name": "Alice", "age": 22}}'
    ```
   and take a look at the output:
   ```json
   {"user":{"name":"Bob","age":22}}
   ```

## Configuration

This configuration sets up an API that replaces the value of the `name` field under the `shop object` in the JSON body with "foo", before forwarding the request to a target service running on localhost at port 3000.
```xml
<api port="2000">
   <replace jsonPath="$.user.name" with="Bob" />
   <return />
</api>
```