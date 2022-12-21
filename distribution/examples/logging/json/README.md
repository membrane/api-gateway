### JSON LOGGING

If desired membrane can log in json format using `JsonTemplateLayout` 
from `log4j2`

#### RUNNING THE EXAMPLE

In this example we will configure membrane to log in `JSON` format

To run the example execute the following steps:

1. Go to the `examples/logging-json` directory.

2. Execute `service-proxy.sh`

3. Notice the file `membrane_json.log` that is created in the `logging-json` directory.

4. Open the URL http://localhost:2000/ in your browser.

5. Open the file `membrane_json.log` and take a look at the logged data.


#### HOW IT IS DONE

The following part describes the example in detail.

Let's take a look at the `service-proxy.sh` file.

```
 java -Dlog4j.configurationFile=$(pwd)/log4j2_json.xml  -Dlog4j.debug=true -classpath "$CLASSPATH" com.predic8.membrane.core.Starter -c proxies.xml
```

In the above line we pass absolute path of our log4j configuration file with `-Dlog4j.configurationFile` VM option.

In the `log4j2_json.xml` file you can see `<JsonTemplateLayout eventTemplateUri="classpath:com/predic8/membrane/core/interceptor/log/logTemplate.json"/>`
Membrane comes with a `JSON` template that attaches the proxy name to log output for easier tracing and log handling.
`JsonTemplateLayout` class also comes with predefined `JSON` layouts like `EcsLayout` and `GelfLayout`. For more information check out [link](https://logging.apache.org/log4j/2.x/manual/json-template-layout.html). 

You can also use your own `JSON` layout using `eventTemplateUri` property. In order to observe this you can open `logging-json/log4j2_json` file and edit
`eventTemplateUri` to `file:(ABSOLUTE PATH OF ExampleLayout.json)` for example `file:/home/user/ExampleLayout.json`
