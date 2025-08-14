# OpenAPI + JWT Auth with Scope Enforcement

This example demonstrates how to secure an API using JWT authentication with scope validation.

## Features

1. **Issue JWT tokens** via a local token server.
2. Use an **OpenAPI specification** with scope definitions.
3. **Automatic scope enforcement** by Membrane (`validateSecurity="yes"`).

---

## OpenAPI Specification with Scopes

In `proxies.xml`, reference the OpenAPI file:

```xml
<openapi location="secure-shop-api.yml" validateSecurity="yes"/>
```

This makes Membrane automatically enforce the security rules defined in the spec.

**secure-shop-api.yml**:

```yaml
security:
  - jwt: []  # JWT required globally

paths:
  /products:
    get:
      security:
        - jwt: [inventory]   # requires inventory scope
    post:
      security:
        - jwt: [management]  # requires management scope
```

**Rules:**

* Every request must include a valid JWT.
* `GET /products` → requires `inventory` scope.
* `POST /products` → requires `management` scope.

---

## 1. Generate a JSON Web Key (JWK)

```bash
./membrane.sh generate-jwk -o ./jwk.json
```

---

## 2. Configure `proxies.xml`

**Token Server**:

```xml
<!-- Token Server -->
<api port="2000" name="Token Server">
  <request>
    <template>{
      "sub": "user@example.com",
      "aud": "shop",
      "scp": "inventory"
    }</template>
    <jwtSign>
      <jwk location="jwk.json"/>
    </jwtSign>
  </request>
  <return/>
</api>
```

**Protected API**:

```xml
<!-- Protected API -->
<api port="2001" name="Protected API">
  <!-- OpenAPI with scope enforcement -->
  <openapi location="secure-shop-api.yml" validateSecurity="yes"/>
  
  <!-- JWT verification -->
  <jwtAuth expectedAud="shop">
    <jwks>
      <jwk location="jwk.json"/>
    </jwks>
  </jwtAuth>
  
  <openapiValidator/>
</api>
```

---

## 3. Start Membrane

```bash
./membrane.sh
```

**Endpoints:**

* **Port 2000** → Token server
* **Port 2001** → Protected API with scope enforcement

---

## 4. Request a Token

```bash
curl http://localhost:2000
```

Example response:

```
eyJhbGciOi...GyFA
```

The token includes `scp: "inventory"`, which satisfies the `GET /products` scope requirement.

---

## 5. Access the Protected API

```bash
curl -H "Authorization: Bearer <your-token>" http://localhost:2001/shop/v2/products
```

Example response:

```json
{
  "products": [
    {
      "id": 1,
      "name": "Banana",
      "self_link": "/shop/v2/products/1"
    }
  ]
}
```