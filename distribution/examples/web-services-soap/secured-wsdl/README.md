# Securing a WSDL

## How it works
- The `soapProxy` acts as an entry point, listening on port `2010` and forwarding WSDL requests to the internal `wsdl-proxy`.
- The `wsdl-proxy` provides full authentication configuration.
- To secure a WSDL service, replace `<sampleSoapService/>` with a <target> definition specifying the actual WSDL endpoint. Authentication settings should be configured within the `wsdl-proxy` to ensure proper access control.

**Refer to [`proxies.xml`](./proxies.xml) for further details, settings, and modifications.**

