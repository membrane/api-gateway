# Padding Header

This guide demonstrates how to use the Padding Header.

## Running the Sample

1. Go to the `examples/security/padding-header` directory.
2. Run `service-proxy.sh` or `service-proxy.bat` to start the API Gateway.
3. Send a request to http://localhost:2000:
```
curl http://localhost:2000 -v
```
4. Examine the X-Padding header in the response, which will look something like this:

```X-Padding: p,@7;#G=:}vbx`#Rd{wqtA0W;FxM1```

5. Repeat the request multiple times and observe how the `X-Padding header changes with each request. 