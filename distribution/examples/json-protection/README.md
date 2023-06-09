# Protecting against JSON attacks - jsonProtection Example

Membrane has the ability to validate JSON files, identifying misuse such as duplicate fields, unusually large arrays or strings, and excessively nested documents. This allows Membrane to prevent potentially harmful JSON from compromising API services.

### Running the example

Make sure to use Membrane 5.2 or newer.

1. Enter the _examples/json-protection_ directory.
2. Start Membrane using the included shell script. 
 
Linux
```shell
./service-proxy.sh
```
Windows
```batch
service-proxy.bat
```
<br/>

3. Post JSON to ```http://localhost:2000``` there are multiple possible methods:

* Run the shell script provided for your platform. (Requires ```curl``` to be installed, ```python``` for JSON output.)

Linux
```shell
./requests.sh
```
Windows
```batch
requests.bat
```

* Run individual requests inside ```requests.http``` using editors or IDEs supporting ```.http``` files.
* Use tools like ```curl``` to post the JSON files in the ```./requests``` directory manually.
<br/>
<br/>

**The requests will test every possible case of malicious JSON data.  
Take a look at the ```proxies.xml``` file to get an idea of how to set up the jsonProtection plugin.  
Aditionally you can take a look at the element reference:**

#### See:  
* [jsonProtection](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/jsonProtection.htm) reference
