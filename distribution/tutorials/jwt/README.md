# JWT Authentication Tutorial

Learn how to protect an API with JSON Web Tokens (JWT). A client exchanges its
credentials for a short-lived, signed token and then uses that token as a Bearer
token on each request, while the gateway validates the signature, expiry and
audience on every call.

Each step is explained directly in the configuration file, which is also the
Membrane config you run. If possible, use an editor with YAML support such as
Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [10-JWT-Requesting-Token.md](10-JWT-Requesting-Token.md) — a `curl`-only walkthrough of the
   hosted [Membrane demo](https://www.membrane-api.io/jwt/jwt-api-authentication-authorization-tutorial.html):
   request a token via the OAuth2 Client Credentials flow and use it to call a
   protected API. Nothing to run locally.
2. [20-JWT-Signing.yaml](20-JWT-Signing.yaml) — sign and validate JWTs with the
   jwtSign interceptor, a lightweight alternative to a full OAuth2 token endpoint.

## Next Steps

Start with [10-JWT-Requesting-Token.md](10-JWT-Requesting-Token.md), then run
[20-JWT-Signing.yaml](20-JWT-Signing.yaml).

For a full OAuth2 setup — an authorization server and a token-validating gateway — see the
[OAuth2 tutorial](../oauth2).
