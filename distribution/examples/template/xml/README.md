# Template Plugin: XML Samples 

This sample demonstrates, how to create XML from a template and how to read XML from a request.

Use the `template` plugin to create body content from a template. Placeholders in the template can be filled with values from variables. See the [text template](../text) sample for how to access variables. 

## Running the Example

Execute the following steps:

1. Go to the `examples/template/xml` directory.

2. Have a look at `proxies.xml`.

3. Open a commandline and execute `service-proxy.sh` or `service-proxy.bat` 

4. Run this command from a second commandline: 

   ```bash
   ❯ curl -d '<person firstname="Juan"/>' -H 'content-type: application/xml'  http://localhost:2000
   Buenas Noches, Juansito!
   ```

5. To run the second example execute:

   ```bash
   ❯ curl -d @cities.xml -H 'content-type: application/xml'  http://localhost:2001
   <destinations>
       <answer>42</answer>
       <destination>Hong Kong</destination>
       <destination>Tokio</destination>
       <destination>Berlin</destination>
   </destinations>

---
See:
- [template](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/template.htm) reference 