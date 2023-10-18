# Access Log Interceptor

The access log interceptor provides a set of common and useful exchange properties to use in a `log4j2_access.xml` pattern layout configuration.
The default implementation provides a [Apache Common Log](https://httpd.apache.org/docs/trunk/logs.html#common) pattern.

You can provide optional `<additionalVariable>` to extend or override the default configuration.

## Running the example

This example will visit a website and logs the access to the console. We use some predefined variables already declared in
the `AccessLogInterceptor` and some additional variables to extend the default behaviour.

1. Run `examples/logging/access/service-proxy.sh`
2. Open your browser or curl http://localhost:2000
3. Check if membrane logged something like `127.0.0.1 "GET / HTTP/1.1" 200 0 - application/json`





