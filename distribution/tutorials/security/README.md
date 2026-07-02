# JWT Authentication Tutorial

Learn how to protect an API with JSON Web Tokens (JWT). A client exchanges its
credentials for a short-lived, signed token and then uses that token as a Bearer
token on each request, while the gateway validates the signature, expiry and
audience on every call.

Each step is explained directly in the configuration file, which is also the
Membrane config you run. If possible, use an editor with YAML support such as
Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [40-JWT-Requesting-Token.md](40-JWT-Requesting-Token.md) — a `curl`-only walkthrough of the
   hosted [Membrane demo](https://www.membrane-api.io/jwt/jwt-api-authentication-authorization-tutorial.html):
   request a token via the OAuth2 Client Credentials flow and use it to call a
   protected API. Nothing to run locally.
2. [41-JWT-Signing.yaml](41-JWT-Signing.yaml) — sign and validate JWTs with the
   jwtSign interceptor, a lightweight alternative to a full OAuth2 token endpoint.

## Next Steps

Start with [40-JWT-Requesting-Token.md](40-JWT-Requesting-Token.md), then run
[41-JWT-Signing.yaml](41-JWT-Signing.yaml).

# OAuth2 Tutorial

Run a complete OAuth2 setup with Membrane acting as both the authorization
server and the token-validating gateway. The tutorials build on each other,
from simple to advanced:

1. [50-OAuth2-Basics.yaml](50-OAuth2-Basics.yaml) — the smallest complete OAuth2 loop:
   get a token, call a protected API, watch the gateway validate it.
2. [51-OAuth2-Client-Credentials.yaml](51-OAuth2-Client-Credentials.yaml) — machine-to-machine
   access with signed JWTs carrying audience and scope claims.
3. [52-OAuth2-Password-Flow.yaml](52-OAuth2-Password-Flow.yaml) — a user logs in with
   username and password, adding a second identity to the token.
4. [53-OAuth2-Client-Token-Renewal.yaml](53-OAuth2-Client-Token-Renewal.yaml) — the gateway
   fetches and renews tokens transparently for its callers.
5. [54a-OAuth2-Distributed-Issuer.yaml](54a-OAuth2-Distributed-Issuer.yaml) +
   [54b-OAuth2-Distributed-Validation.yaml](54b-OAuth2-Distributed-Validation.yaml) — run the
   issuer and the validating gateway as two separate instances; the validator fetches the
   issuer's public keys over its JWKS endpoint.

Start with [50-OAuth2-Basics.yaml](50-OAuth2-Basics.yaml) and follow the
instructions in the file.
