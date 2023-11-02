# Using Opentelemetry with Membrane

Membrane offers OpenTelemetry Support for 

Using the `opentelemetry` element you can enrich the header of incoming HTTP requests
with `traceparent` and have your trace send to a jaeger backend.

to do so, set up the jaeger all-in-one docker container with:
```dockerfile
docker run -d --name jaeger -e COLLECTOR_OTLP_ENABLED=true -p 16686:16686 -p 4317:4317 -p 4318:4318 jaegertracing/all-in-one:latest
```

Setup the proxies.xml like:
```xml

<router>
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
    <api port="2000">
        <opentelemetry
                jaegerPort="4317"
                jaegerHost="localhost"
                sampleRate="1.0"
        />
        <target host="localhost" port="3000"/>
    </api>
</router>

```

The first `<api>` entry is meant to simulate another OpenTelemetry service and serves as an example.
If you already run an OpenTelemetry Service, you just need the second entry.
Run a request to `localhost` with `curl http://localhost:2000?name=membrane`.
You should see `{ "hello": membrane! }` in your terminal and a trace,
created by Membrane should be visible in the [jaeger frontend](http://localhost:16686).

![JaegerFrontend](./resources/membrane_opentelemetry_example.png)

