# Using Opentelemetry with Membrane

Membrane offers support for tracing with [OpenTelemetry](https://opentelemetry.io/).

Using the `opentelemetry` element you can enrich the header of incoming HTTP requests and responses
with trace data.

## Run the Example

1. Setup jaeger with:
```dockerfile
docker run -d --name jaeger -e COLLECTOR_OTLP_ENABLED=true -p 16686:16686 -p 4317:4317 -p 4318:4318 jaegertracing/all-in-one:latest
```

2. Run `service-proxy.bat` or `./service-proxy.sh` in this folder.

3. Run a request `curl http://localhost:2000?name=membrane`.

4. You should see `{ "hello": membrane! }` in your terminal and a trace,
   created by Membrane should be visible in the [jaeger frontend](http://localhost:16686).

5. Take a look into the `proxies.xml
```xml

<router>
    <api port="2000">
        <opentelemetry
                jaegerPort="4317"
                jaegerHost="localhost"
                sampleRate="1.0"
        />
        <target host="localhost" port="3000"/>
    </api>

    <api port="3000" method="GET">
        <opentelemetry
                jaegerPort="4317"
                jaegerHost="localhost"
                sampleRate="1.0"
        />
        <request>
            <template contentType="application/json" pretty="yes">
                { "hello": ${params.name}! }
            </template>
        </request>
        <return statusCode="200"/>
    </api>
</router>

```

If you already run an OpenTelemetry Service, you just need the first entry.
The second `<api>` entry is meant to simulate another OpenTelemetry service and serves as an example.

![JaegerFrontend](./resources/membrane_opentelemetry_example.png)

