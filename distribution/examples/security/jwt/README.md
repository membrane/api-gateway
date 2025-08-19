## JWT Example Recipes for Membrane API Gateway

### Available Example Recipes

*  **JWT Scope Validation**
   Secure an API by verifying that the JWT includes specified scopes, e.g., enforcing `"write"` permission on API access.
   See: [How to secure APIs with JWT and Scope Validation](https://www.membrane-api.io/recipes/jwt_scope_check.html).

*  **JWT Security for OpenAPI‑Defined APIs**
   Apply JWT authentication and enforce scope‑based access control defined in your OpenAPI spec—for instance, restricting certain operations to `"inventory"` or `"management"` scopes.
   See: [How To: Secure OpenAPI resources using JWTs and RBAC](https://www.membrane-api.io/recipes/openapi-jwt-security.html).

*  **Forwarding JWT Payload Claims into HTTP Headers**
   Extract a custom claim (e.g., `organization`) from a JWT and forward it to your backend service in a specified HTTP header (e.g., `X‑Org`).
   See: [How To: Forward a Custom Claim from JWT into HTTP Header](https://www.membrane-api.io/recipes/jwt_payload_to_http_header.html).

### Summary

| Recipe Name                 | Key Benefit                                   |
| --------------------------- | --------------------------------------------- |
| JWT Scope Validation        | Enforce specific scopes (like `"write"`)      |
| OpenAPI JWT Security + RBAC | Scope-based access via OpenAPI definitions    |
| JWT Claim to HTTP Header    | Make JWT claims available to backend services |

[1]: https://www.membrane-api.io/recipes/jwt_scope_check.html "https://www.membrane-api.io/recipes/jwt_scope_check.html"
[2]: https://www.membrane-api.io/recipes/openapi-jwt-security.html "How to secure OpenAPI Resources using JWTs and RBAC"
[3]: https://www.membrane-api.io/recipes/jwt_payload_to_http_header.html "Forward a Custom Claim from JWT into HTTP Header"
