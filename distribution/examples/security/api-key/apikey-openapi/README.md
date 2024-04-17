# Authorization with API Keys and OpenAPI

This example shows how to use API keys with role-based access control (RBAC). An API key can have associated scopes(roles) that are matched against security definitions in OpenAPI documents.

## 1. Accessing the API
1. **Navigate** to the `examples/security/api-key/apikey-openapi` directory.
2. **Start** the API Gateway by executing `service-proxy.sh` (Linux/Mac) or `service-proxy.bat` (Windows).
3. Open `http://localhost:2000/api-docs`.
4. Click on `Fruit Shop API`
5. Expand 'GET /products', click on `Try it out` and then on `execute`.
6. Observe the error message `"Authentication by API key is required."`

## 2. Using an API Key (No Scopes)

1. Click on "Authorize" in the Swagger UI.
2. Enter `111` as API Key and click `Authorize`.
3. Retry a call to `GET /products`. Now the access should be successful.
4. Try POST, PUT, PATCH, or DELETE and observe the 403 status code. Endpoints with these methods require a special scope(role).

## 3. API Key with write Permission

1. Authorize using API key `222` in the upper right corner. This key has the "write" scope.
2. Now attempt POST, PUT, PATCH or DELETE requests to see the expanded access.

## Configuration References

- **API Key File Store:**

  ```xml
  <spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">
      <spring:property name="location" value="./demo-keys.txt" />
  </spring:bean>
  ```
  This configures a Spring Bean which provides the keys and associated scopes from the file `./demo-keys.txt` to all apiKeys plugins.

- **Demo Keys File (`demo-keys.txt`):**

  This file contains the API keys along with their associated scopes.
  ```
  # Simple key without any scope
  111
  # Keys associated with roles/scopes
  222: write
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

  To understand the `proxies.xml` configuration read [here](../simple/README.md)


  - **OpenAPI Configuration:**
  
    - Global security:
      ```yaml
      security:
        - http: []
      ```
      Now every endpoint requires a valid API Key (regardless of scope). 

    - Configuration for `POST`, `PUT`, `PATCH` and `DELETE` endpoints:
      ```yaml
      security:
        - http:
          - write
      ```  
      Now `POST`, `PUT`, `PATCH` and `DELETE` endpoints require a valid API Key with the scope `write`.
    
    - Security Scheme:
      ```
      securitySchemes:
        http:
          type: apiKey
          in: header
      ```