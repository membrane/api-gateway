# Membrane API Gateway Tutorial - Web Services Security

This tutorial shows how to use the `wsSecurity` interceptor to secure SOAP web services with
WS-Security. The tutorials build on each other, from simple to advanced:

1. [10-Add-UsernameToken.yaml](10-Add-UsernameToken.yaml) — add a `wsse:UsernameToken` and a
   `wsu:Timestamp` to an incoming SOAP request.
2. [20-Validate-UsernameToken.yaml](20-Validate-UsernameToken.yaml) — require a valid
   `wsse:UsernameToken` on every incoming request before it reaches the backend.
3. [30-Add-And-Validate-Timestamp.yaml](30-Add-And-Validate-Timestamp.yaml) — add a
   `wsu:Timestamp` on one API and validate it (including expiry) on another.
4. [40-Validate-Signed-Body.yaml](40-Validate-Signed-Body.yaml) — require a valid `ds:Signature`
   over the SOAP body before a request reaches the backend.
5. [50-Sign-And-Validate-Body.yaml](50-Sign-And-Validate-Body.yaml) — sign the body on one API
   and verify the signature on another, using a shared keypair.
6. [60-Full-Signature-Example.yaml](60-Full-Signature-Example.yaml) — combine a timestamp, a
   UsernameToken and a signature covering all of them plus an XPath-selected element.

Start with [10-Add-UsernameToken.yaml](10-Add-UsernameToken.yaml) and follow the instructions
in the file.


