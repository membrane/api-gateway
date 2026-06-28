# JWT Authentication Tutorial

Learn how to protect an API with JSON Web Tokens (JWT). A client exchanges its
credentials for a short-lived, signed token and then uses that token as a Bearer
token on each request, while the gateway validates the signature, expiry and
audience on every call.

Each step is explained directly in the configuration file, which is also the
Membrane config you run. If possible, use an editor with YAML support such as
Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [40-Requesting-a-JWT.md](40-Requesting-a-JWT.md) — a `curl`-only walkthrough of the
   hosted [Membrane demo](https://www.membrane-api.io/jwt/jwt-api-authentication-authorization-tutorial.html):
   request a token via the OAuth2 Client Credentials flow and use it to call a
   protected API. Nothing to run locally.
2. [50-Issuing-and-Validating-JWTs.yaml](50-Issuing-and-Validating-JWTs.yaml) — let Membrane itself
   issue and validate the tokens, fully offline.

## Next Steps

Start with [40-Requesting-a-JWT.md](40-Requesting-a-JWT.md), then run
[50-Issuing-and-Validating-JWTs.yaml](50-Issuing-and-Validating-JWTs.yaml).
