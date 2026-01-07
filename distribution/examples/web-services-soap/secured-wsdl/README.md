# Using a secured WSDL for a SOAP Proxy

Usually WSDL documents are not secured and can be freely accessed by anyone. But sometimes WSDL documentation is considered sensitive and is protected by access control.

To reference a secured WSDL in Membrane, configure an internal API to handle authentication and authorization with the server hosting the WSDL.

See the `apis.yaml` for a starting point.

