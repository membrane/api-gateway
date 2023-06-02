# Template Plugin: Text Samples 

This sample demonstrates, how to create text from a template.

Use the `template` plugin to create body content from a template. Placeholders in the template can be filled with values from variables. 

## Running the Example

Execute the following steps:

1. Go to the `examples/template/text` directory.

2. Have a look at `proxies.xml`.

2. Open a commandline and execute `service-proxy.sh` or `service-proxy.bat` 

3. Run this command from a second commandline: 

   ```bash
   ❯ curl http://localhost:2000/text?name=Joe
   Hello Joe!
   ```

4. Then execute:

   ```bash
   curl http://localhost:2000/text?name=Joe     
   ```

---
See:
- [template](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/template.htm) reference 