# SSL/TLS Examples

| Example                                    | Description                                                                                                                   |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| [to-backend](to-backend)                   | How to secure communication from the API Gateway to a backend server.                                                         |
| [api-with-tls-pkcs12](api-with-tls-pkcs12) | How to protect APIs deployed on the Gateway with TLS certificates (using a keystore in the PKCS12 format)"                    |
| [api-with-tls-pem](api-with-tls-pem)       | How to protect APIs deployed on the Gateway with TLS certificates (using PEM formatted files for the key and the certificate) |

# SSL/TLS Errors
Depending on your use case for Membrane API Gateway, you may or may not want to set `<ssl showSSLExceptions="false">` (default) or `<ssl showSSLExceptions="true">`.

In case you are setting up your keys and certificates for the first time, or you are searching for TLS misconfigurations,
we advise `<ssl showSSLExceptions="true">`. This is why all examples in this folder have this setting.

But when Membrane API Gateway is directly exposed to the internet on port 443, you most probably want to turn it off, because too much "SPAM" is arriving.
