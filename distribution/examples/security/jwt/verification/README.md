# Verifying JWTs

Protecting an API by requiring the caller to send a valid JWT.

## Running the Sample
***Note:*** *The requests are also available in the requests.http file.*

1. **Navigate** to the `examples/security/jwt/verification` directory.
2. **Start** the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Use it to access a protected endpoint**:
   ```
   curl http://localhost:2001 -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Im1lbWJyYW5lIn0.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiYXVkIjoib3JkZXIiLCJzY29wZSI6ImV4ZWN1dGUiLCJpYXQiOjE3Njc4MDMwODYsImV4cCI6MjA3NTM4NzA4NiwibmJmIjoxNzY3ODAyOTY2fQ.V661gIICLKzg3cAZIhl1632X-H_jQZSfuUjbqqxfShSpJZSBwqMvRLGHTso107miBnnLweNYTMxOjBoPrC5QruDcXbz32RzdGD9rY6uy47ewDCw6W4fzsETjjxstqebYQJIKiR1mGKOi418kv5Vecw23TvtrezRf8bq1ElzljcKzOioYnrxq4fyT8V9uCFccqub5WqMt8DaddfJGMt_WTLmFu_MlU4UMDdghF8wFgFaqGg-J5gQRd-EevfNc1DIw1s-Pu4nEjAgpZ94a1dMX7IbmcrONLrbtjJzoouMeKc_hJyeW8XWsggmULbOOI5sz2T-PL-MgmtQMk8Ewx37QWQ"
   ```
## Understanding the Configuration

When you receive a JWT from an untrusted source, you should
* check the signature against a trusted key. (In this example, jwk.json contains this key.)
* check that the JWT contains the correct `aud` (audience) claim for your service. (In this example, we use `order` as the audience.)
* check that the JWT is currently valid, that is, that the `exp` (expiry) claim is in the future and `nbf` (not before) is in the past. (For this demo, we created a JWT with a validity period of 10 years. In practice, you should use MUCH shorter validity periods like 5 minutes.)

This is what Membrane's `jwtAuth` does.

### JWT Validation

We use the JWT to access the protected resource. 
`jwtAuth` validates the JWT against the public key from the `jwk.json` file, and enforces the audience to be `order`. 
If the JWT is valid, we extract the scopes and return them.

```yaml
api:
  port: 2001
  name: Protected Resource
  flow:
    - jwtAuth:
        expectedAud: order
        jwks:
          jwks:
            - jwk:
                location: jwk.json
    - template:
        src: |
          You accessed protected content!
          JWT Scopes: ${property.jwt.get("scope")}
    - return: {}
```