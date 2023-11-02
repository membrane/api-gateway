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
        <!--... other routes... -->
        <api port="2000">
            <opentelemetry
            jaegerPort="4317"
            jaegerHost="localhost"
            sampleRate="1.0"
            />
            <target host="localhost" port="8081"/>
        </api>

    </router>

```

Run a request to `localhost` with `curl localhost:2000`.
Even if you haven't setup another OpenTelemetry service and receive a 404 "Not found" error, the trace created in Membrane
should be visible in the [jaeger frontend](http://localhost:16686).


