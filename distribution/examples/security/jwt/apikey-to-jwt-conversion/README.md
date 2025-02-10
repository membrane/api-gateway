# Dynamically constructing JWTs from API Keys

Convert scoped API keys to JWTs dynamically with customizable payload.

## Running the Sample
***Note:*** *The requests are also available in the requests.http file.*

1. **Navigate** to the `examples/security/jwt/apikey-to-jwt-conversion` directory.
2. **Start** the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Fetch JWT**:
   ```
   curl http://localhost:2000 -H "X-Api-Key: 123456789"
   ```
4. **Use it to access a protected endpoint**:
   ```
   curl http://localhost:2001 -H "Authorization: Bearer <INSERT JWT HERE>"
   ```
## Understanding the Configuration

### API Key Conversion

To build a JWT, we first need to authenticate the client and store the associated scopes. 
We use the [\<apiKey\>](../../api-key/rbac/README.md) element to extract the value of the `X-Api-Key` header and verify its validity by checking if the `<apiKeyFileStore>` contains the appropriate key. 
If the key is found, we continue with the process and store the API key's scopes in a property named `scopes`.
This property is then used in a [\<template\>](../../../template/json/README.md) to construct our JWT payload. 
After constructing the JWT payload, we pass it to the `<jwtSign>` plugin, which uses the private key from the `jwk.json` file to sign and generate a valid JWT. 
The resulting JWT is stored in the body and returned via `<return>`.

```xml
<api port="2000" name="Authorization Server">
 <apiKey required="true">
  <apiKeyFileStore location="demo-keys.txt" />
  <headerExtractor />
 </apiKey>
 <request>
  <setProperty name="scopes" value="${scopes()}"/>
  <template>
   {
     "sub": "user@example.com", 
     "aud": "order", 
     "scope": "${property.scopes}"
   }
  </template>
  <jwtSign>
   <jwk location="jwk.json"/>
  </jwtSign>
 </request>
 <return />
</api>
```

### JWT Validation

Once we have acquired the JWT, we can use it to access the protected resource. 
`<jwtAuth>` validates the JWT against the public key from the `jwk.json` file, and enforces the audience to be `order`. 
If the JWT is valid, we extract the scopes and return them.

```xml
<api port="2001" name="Protected Resource">
 <request>
  <jwtAuth expectedAud="order">
   <jwks>
    <jwk location="jwk.json" />
   </jwks>
  </jwtAuth>
 </request>
 <template>${property.jwt.get("scope")}</template>
 <return />
</api>
```