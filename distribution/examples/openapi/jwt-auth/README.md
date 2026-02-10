# OpenAPI + JWT Auth with Scope Enforcement

This example demonstrates how to secure an (Open)API using JWT authentication with scope validation. We use OpenAPI's JWT securityScheme to describe the authorization requirements (=required scopes). Membrane API Gateway is configured to automatically enforce the requirements specified in the OpenAPI document.

## Features in this Example

1. **Issue JWT tokens** via a local token server. (This could also be done with Keycloak or Entra ID, for example.)
2. Use an **OpenAPI specification** with scope definitions.
3. **Automatic scope enforcement** by Membrane (`validateSecurity="yes"`).

---

## OpenAPI Specification with Scopes

In `apis.yaml`, reference the OpenAPI file:

```yaml
  openapi:
    - location: secure-shop-api.yml
      validateSecurity: true
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

## 2. Configure `apis.yaml`

**Token Server**:

(Instead of using Membrane API Gateway as the token server, you can also integrate with Keycloak or Microsoft Entra ID. To avoid extra setup for this demo, we use tokens issued by Membrane itself.)
```yaml
# Token Server
api:
  name: Token Server
  port: 2000
  flow:
    - request:
        - template:
            src: |
              {
                "sub": "user@example.com",
                "aud": "shop",
                "scp": "inventory"
              }
        - jwtSign:
            jwk:
              location: jwk.json
    - return: {}
```

**Protected API**:

```yaml
# Protected API
api:
  name: Protected API
  port: 2001
  openapi:
    - location: secure-shop-api.yml
      validateSecurity: true
  flow:
    - jwtAuth:
        expectedAud: shop
        jwks:
          jwks:
            - jwk:
                location: jwk.json
    - openapiValidator: {}
```

---

## 3. Start Membrane

**macOS/Linux**

```bash
./membrane.sh
```

**Windows**
```bat
membrane.cmd
```

**Endpoints:**

* **Port 2000** → Token server
* **Port 2001** → Protected API with scope enforcement

---

## 4. Try to access the Protected API

```cmd
curl http://localhost:2001/shop/v2/products
```

---

## 5. Request a Token

```cmd
curl http://localhost:2000
```

Example response:

```
eyJhbGciOi...GyFA
```

The token includes `scp: "inventory"`, which satisfies the `GET /products` scope requirement.

---

## 6. Access the Protected API

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