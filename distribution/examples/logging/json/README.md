### JSON LOGGING

If desired membrane can log in json format using `JsonTemplateLayout` from `log4j2`

#### RUNNING THE EXAMPLE

In this example we will configure membrane to log in `JSON` format

To run the example execute the following steps:

1. Go to the `examples/logging/json` directory.

2. Set the Log4j config via `JAVA_OPTS`:

   **Linux/macOS**
   ```sh
   export JAVA_OPTS="-Dlog4j.configurationFile=examples/logging/json/log4j2_json.xml -Dlog4j.debug=true"
   ```
    
    **Windows (PowerShell)**
    
    ```powershell
    $env:JAVA_OPTS='-Dlog4j.configurationFile=examples/logging/json/log4j2_json.xml -Dlog4j.debug=true'
    ```
    
    Note: relative paths are resolved against `$MEMBRANE_HOME`. Absolute paths and URIs are left unchanged.

3. Execute `membrane.sh`

4. Notice the file `membrane_json.log` that is created in the `logging-json` directory.

5. Open the URL http://localhost:2000/ in your browser.

6. Open the file `membrane_json.log` and take a look at the logged data.


#### HOW IT IS DONE

The following part describes the example in detail.

The Log4j2 configuration is selected via the JVM system property `-Dlog4j.configurationFile` (provided through `JAVA_OPTS`).

In the `log4j2_json.xml` file you can see `<JsonTemplateLayout eventTemplateUri="classpath:com/predic8/membrane/core/interceptor/log/logTemplate.json"/>`
Membrane comes with a `JSON` template that attaches the proxy name to log output for easier tracing and log handling.
`JsonTemplateLayout` class also comes with predefined `JSON` layouts like `EcsLayout` and `GelfLayout`. For more information check out [link](https://logging.apache.org/log4j/2.x/manual/json-template-layout.html). 

You can also use your own `JSON` layout using `eventTemplateUri` property. In order to observe this you can open `logging-json/log4j2_json` file and edit
`eventTemplateUri` to `file:(ABSOLUTE PATH OF ExampleLayout.json)` for example `file:/home/user/ExampleLayout.json`

---
See:
- [log](https://membrane-api.io/docs/current/log.html) reference