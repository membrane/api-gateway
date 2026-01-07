# Tracing with OpenTelemetry

Membrane supports distributed tracing based on the [OpenTelemetry](https://opentelemetry.io/) specification.

With the OpenTelemetry plugin, API traffic can be observed end to end. Membrane collects tracing data for requests flowing through the gateway and exports it to an OTLP endpoint. In this example, Jaeger is used as the backend.

To instrument an API, add the `openTelemetry` plugin to the API configuration.

## Run the Example

1. Start Jaeger with:
```dockerfile
docker run -it --name jaeger -e COLLECTOR_OTLP_ENABLED=true -p 16686:16686 -p 4317:4317 -p 4318:4318 jaegertracing/all-in-one:latest
```

2. Start Membrane: `membrane.cmd` or `./membrane.sh`

3. Call the first endpoint in the telemetry chain:

   ```bash
   curl http://localhost:2000
   ```

4. You should see `Greetings from the backend!` in your terminal.
5. Open the Jaeger UI in your browser: `http://localhost:16686`
6. Select Membrane as the service and click on `Find Traces`.
A span created by Membrane should be visible in the [Jaeger UI](http://localhost:16686).
![sample](resources/otel_example.png)
7. Check the headers printed to the console. You will see headers such as `traceparent`, which indicate the trace and span context involved in the request.

**How it is done**

Take a look at the `apis.yaml`.

The openTelemetry plugin can be used in several ways:

a.) Globally, in a shared interceptor chain that applies to all APIs (as shown in apis.yaml)
b.) Per API, by defining it directly inside the API flow
c.) With reusable interceptor chains. See: [Reusable Plugin Chains](../../extending-membrane/reusable-plugin-chains)

Example configuration for a single API:

```yaml
api:
  port: 2000
  flow:
    - openTelemetry:
        sampleRate: 1.0
        otlpExporter:
          host: localhost
          port: 4317
          transport: grpc
  target:
    url: http://localhost:2001
```

**Note on Transport:**

The OTLP exporter is configured by default to use gRPC. To use HTTP, set the `transport` field to `http` and set the `port` to `4318`.
