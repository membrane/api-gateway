# Swagger UI: API Key Authorization

Enhance security using API keys with role-based access control (RBAC).

## Accessing the API

1. Navigate to `localhost:2000/api-docs`.
2. Execute GET Requests without an API key to understand initial restrictions.
3. Note the limited access to POST, PUT, PATCH, or DELETE requests.

## Step 1: API Key 111 (No Scopes)

1. Click "Authorize" in Swagger UI.
2. Enter API key `111` and click "Authorize".
3. Retry GET requests to observe the access granted.
4. Note the continued restrictions on modifying requests.

## Step 2: API Key 222 (Write Scope)

1. Authorize as before, using the key `222` with "write" scope.
2. Now execute POST, PUT, and PATCH requests successfully.
3. Observe that DELETE requests are still restricted.

## Step 3: API Key 333 (Write, Delete Scopes)

1. Authorize as before, using the key `222` with "write" and "delete" scopes.
2. Execute DELETE requests and observe the full access granted.
3. Explore the API with complete functionality.

## Configuration References

- **API Key File Store:**

  ```xml
  <spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">
      <spring:property name="location" value="./demo-keys.txt" />
  </spring:bean>
  ```

- **Proxies.xml Configuration:**

  ```xml
      <api port="2000">
          <openapi location="fruitshop-api-v2-openapi-3-security.yml" validateRequests="yes" validationDetails="yes"/>
          <apiKey required = "false">
              <headerExtractor />
          </apiKey>
      </api>
  ```

- **Demo Keys File (`demo-keys.txt`):**

  ```
  # Simple key without any scope
  111
  # Keys associated with roles/scopes
  222: write
  333: write,delete
  ```

- **OpenAPI YAML Configuration:**

  Configuration for `POST`, `PUT` and `PATCH` endpoints:
  ```yaml
  security:
    - http:
      - write
  ```  
  Configuration for `DELETE` endpoints:
  ```yaml
  security:
    - http:
        - delete
  ```
  Security Scheme:
  ```
  securitySchemes:
    http:
      type: apiKey
      in: header
  ```