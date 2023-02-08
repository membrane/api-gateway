# Template Plugin: JSON Samples 

This sample demonstrates, how to create JSON from a template and how to read JSON.

Use the `template` plugin to create body content from a template. Placeholders in the template can be filled with values from variables. 

## Running the Example

Execute the following steps:

1. Go to the `examples/template/json` directory.

2. Have a look at `proxies.xml`.

2. Open a commandline and execute `service-proxy.sh` or `service-proxy.bat` 

3. Run this command from a second commandline: 

   ```bash
   curl "http://localhost:2000/json?answer=42"
   ```

   The output should be:

   ```json
   { "answer": 42 }
   ```

4. Then execute:

   ```bash
   curl -d '{"city":"Berlin"}' -H "Content-Type: application/json" "http://localhost:2000"
     
   ```

   Expect the response:

   ```text
   City: Berlin  
   ```
