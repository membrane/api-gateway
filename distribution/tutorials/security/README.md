For issuing and validating signed JSON Web Tokens, see the [JWT tutorial](../jwt).

For securing an API with API keys, see the [API Key tutorial](../api-keys).

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
