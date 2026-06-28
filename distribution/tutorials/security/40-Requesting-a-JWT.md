# Requesting a JWT

No setup required, just `curl`. Uses the public Membrane demo at `https://api.predic8.de`.

Based on: <https://www.membrane-api.io/jwt/jwt-api-authentication-authorization-tutorial.html>

## 1. Request a token

```sh
curl -X POST https://api.predic8.de/demo/oauth2/token \
  -u "my-client:my-secret" \
  -d "grant_type=client_credentials"
```

```json
{"access_token":"eyJ0eXAiOiJKV1Qi...","token_type":"bearer","expires_in":300}
```

## 2. Inspect the token

Paste the `access_token` into <https://jwt.io>. A JWT has three parts `header.payload.signature`:

- `sub` — subject (the client id)
- `aud` — audience (the API this token is for)
- `scopes` — permissions granted
- `exp` — expiry (300s)

## 3. Call the protected resource

```sh
curl https://api.predic8.de/demo/resource \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1Qi..."
```

```json
{ "success": true, "user": "my-client", "scopes": "read write" }
```

Try it without the header — the request is rejected.

## Next

Continue with [50-Issuing-and-Validating-JWTs.yaml](50-Issuing-and-Validating-JWTs.yaml)
where Membrane issues and validates the tokens itself.
