# Requesting a JWT

This tutorial shows how a client obtains a JSON Web Token from an authorization server and uses 
it to call a protected API.

You request a token, inspect it, and send it as a Bearer token on a request. 
The gateway that *issues and validates* those tokens is covered in the following tutorials. 
Here you only consume them.

By the end you will know how to get an access token via the OAuth2 client credentials
grant, read its claims, and authenticate an API call with it.

No setup required, just `curl`. The tutorial uses the public demo at `https://api.predic8.de`.

Based on: https://www.membrane-api.io/jwt/jwt-api-authentication-authorization-tutorial.html

## 1. Request a token

```sh
curl -v https://api.predic8.de/demo/oauth2/token -u "my-client:my-secret"  -d "grant_type=client_credentials"
```

Response body:

```json
{"access_token":"eyJ0eXAiOiJKV1Qi...","token_type":"bearer","expires_in":300}
```

## 2. Inspect the token

Paste the `access_token` into <https://jwt.io>. A JWT has three parts separated by dots:

`header.payload.signature`

The **payload** carries the claims that describe the token. Who it is for, what it may do, and how long it is valid. For this demo token they are:

| Claim   | Meaning                              | Example         |
|---------|--------------------------------------|-----------------|
| `sub`   | subject (the client id)              | `my-client`     |
| `aud`   | audience (the API this token is for) | `demo-resource` |
| `scope` | permissions granted                  | `read write`    |
| `iat`   | issued-at (Unix time)                | `1782893176`    |
| `exp`   | expiry (Unix time, `iat` + 300s)     | `1782893476`    |
| `nbf`   | not valid before (Unix time)         | `1782893056`    |


## 3. Call the protected resource

```sh
curl -v https://api.predic8.de/demo/resource -H "Authorization: Bearer eyJ0eXAiOiJKV1Qi..."
```

```json
{ "success": true, "user": "my-client", "scopes": "read write" }
```

## Next

Continue with [41-JWT-Signing.yaml](41-JWT-Signing.yaml)
where Membrane issues and validates the tokens itself.
