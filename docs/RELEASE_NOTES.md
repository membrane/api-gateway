# 7.3.0

## New Features
- OAuth2 authorization server: new `resources` attribute on `client` — an allowlist of audiences (RFC 8707). In the client_credentials grant the `resource` request parameter is validated against it (error `invalid_target`), and the granted resources become the `aud` claim of the issued JWT. Without the parameter, all listed resources are granted.
- OAuth2 authorization server: new `scopes` attribute on `client` — an allowlist of scopes. Requested scopes are validated against it (error `invalid_scope`); granted scopes become the `scope` claim (RFC 9068) and are echoed in the token response.
- `bearerJwtToken` access tokens now carry the standard `iss` (from the server's `issuer`) and a unique `jti` claim (RFC 9068).
- Password grant: extra user attributes `aud` and `scopes` on a `staticUserDataProvider` user become the `aud` and `scope` claims of the user's tokens and survive a refresh.
- `jwtAuth`: new `expectedIss` attribute — rejects tokens whose `iss` claim is missing or different.
- `oauth2authserver`: `userDataProvider` is now optional. Pure machine-to-machine setups (client_credentials) no longer need a user list; user-based flows then answer `access_denied`.
- `jwtAuth` starts even when the JWKS URIs are unreachable (e.g. the issuer boots later); the keys are fetched on the first request that needs them.
- New OAuth2 tutorial series in the distribution (`tutorials/security/50-54`): basics, client credentials with claims, password flow with refresh, automatic token renewal, and distributed issuer/validator.

## Improvements
- Refresh tokens are rotated: a presented refresh token is single-use (OAuth2 Security BCP); only the newly issued one stays valid. Clients that reuse an old refresh token now get `invalid_grant`.
- Rejected tokens are logged: invalid tokens at the userinfo endpoint, unresolvable refresh tokens, `tokenValidator` failures, and JWTs signed by unknown keys. `jwtAuth` error responses include the concrete validation failure.
- The `scopes()` and `hasScope()` built-ins now also read the RFC 9068 `scope` claim, not only the Microsoft Entra ID style `scp`.
- `bearerJwtToken` no longer prints the generated private key into the log, only its key id.
- The password grant validates the client and its allowed grant types before issuing tokens.

## Fixes
- Sessions that were never used after creation (e.g. sessions only backing a refresh token) died on the first cleanup sweep, invalidating refresh tokens within a minute. They now live for the configured session timeout.
- The userinfo endpoint returned 500 instead of 401 for malformed Authorization headers.

# 6.0.0

Membrane Version 6 is a big step forward from Membrane 5. Big parts of the code base were refactored and improved.

## New Features
- setHeader now supports also Groovy, XPath, Jsonpath
- New plugins `call`, `destination`
- API key stores for JDBC and MongoDB

## Improvements
- New flow control through plugins. Not based on a stack of executed interceptors but on the definition in the 'proxies.xml' file.
- New `log` plugin with more features and cleaner configuration. It can now dump the exchange and properties
- ProblemDetails format is used for most of the error messages
- Ordered fields in ProblemDetails
- References in OpenAPI are now supported
- In SpEL and Groovy plugins besides `headers.` and `properties.` now also the singulars `header.` will work
