# API Greasing with JSON

Modify JSON documents by injecting random fields or shuffling field order without damaging the integrity of existing data.

## Running the Sample
***Note:*** *The request is also available in the requests.http file.*

1. **Navigate** to the `examples/api-testing/api-greasing` directory.
2. **Start** the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Access** the greased API:
   ```
   curl -v http://localhost:2000 \
   -H "Content-Type: application/json" \
   -d '{
      "name": "John Doe",
      "age": 30,
      "address": {
         "street": "123 Main St",
         "city": "Anytown"
      }
   }'
   ```
4. **Observe** how the JSON from within the `proxies.xml` has been altered.
   A header `X-Grease` will be visible as well now, containing a changelog of what has been modified.
5. **Try it** yourself, use port `2001` and post any JSON document. 

## Understanding the Configuration

```xml
<response>
   <beautifier />
   <greaser>
      <greaseJson shuffleFields="true" additionalProperties="true" />
   </greaser>
</response>
<return />
```
Greasing can be enabled by placing the `<greaser>` inside the configuration with a greasing module inside. In this example we utilize the `<greaseJson>` module, this will allow us to shuffle the JSON document's fields and inject additional properties. To make the outputted JSON easier to read, a `<beautifier />` is added after the greaser.