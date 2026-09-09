# OAuth2 Tutorial

Run a complete OAuth2 setup with Membrane acting as both the authorization
server and the token-validating gateway.

Each step is explained directly in the configuration file, which is also the
Membrane config you run. If possible, use an editor with YAML support such as
Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [10-OAuth2-Basics.yaml](10-OAuth2-Basics.yaml) — the smallest complete OAuth2 loop:
   get a token, call a protected API, watch the gateway validate it.
2. [20-OAuth2-Client-Credentials.yaml](20-OAuth2-Client-Credentials.yaml) — machine-to-machine
   access with signed JWTs carrying audience and scope claims.
3. [30-OAuth2-Password-Flow.yaml](30-OAuth2-Password-Flow.yaml) — a user logs in with
   username and password, adding a second identity to the token.
4. [40-OAuth2-Client-Token-Renewal.yaml](40-OAuth2-Client-Token-Renewal.yaml) — the gateway
   fetches and renews tokens transparently for its callers.
5. [50a-OAuth2-Distributed-Issuer.yaml](50a-OAuth2-Distributed-Issuer.yaml) +
   [50b-OAuth2-Distributed-Validation.yaml](50b-OAuth2-Distributed-Validation.yaml) — run the
   issuer and the validating gateway as two separate instances; the validator fetches the
   issuer's public keys over its JWKS endpoint.

## Next Steps

Start with [10-OAuth2-Basics.yaml](10-OAuth2-Basics.yaml) and follow the
instructions in the file.
