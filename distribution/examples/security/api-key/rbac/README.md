# API Key Authentication and Authorization

Secure endpoints using API keys combined with role-based access control (RBAC).

## Running the Sample
***Note:*** *The requests are also available in the requests.http file.*

1. **Navigate** to the `examples/security/api-key/rbac` directory.
2. **Start** the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Test Optional API Key with RBAC**:
    - Access with a non-admin scope key to receive limited access:
      ```
      curl http://localhost:2000 -H "X-Key: 123456789" -v
      ```
    - Use an admin scope key for admin access:
      ```
      curl http://localhost:2000 -H "X-Key: key_321_abc" -v
      ```
    - Make a request without any key to see default behavior:
      ```
      curl http://localhost:2000 -v
      ```

## Understanding the Configuration

### Key Stores
Key stores maintain API keys and their corresponding scopes through various methods. In this instance, we use the ApiKeyFileStore, which holds the keys within the local file system. 
File stores can either be globally registered for all apiKey plugins via a component declaration or configured locally by embedding them directly within the apiKey plugin:

```yaml
components:
   keys:
     apiKeyFileStore:
       location: ./demo-keys.txt
```
**OR**
```yaml
apiKey:
  ...
  stores:
     - apiKeyFileStore:
        location: ./demo-keys.txt

```
###  Optional API Key with RBAC
The configuration for port `2000` involves optional API key authentication with additional checks for user roles or scopes, as well as employing a custom header for API key extraction.  
By using the conditional "if" plugin, we can check and validate provided scopes using built-in functions.

```yaml
api:
   port: 2000
   flow:
      - apiKey:
           required: false
           extractors:
              - header:
                   name: X-Key
      - setProperty:
           name: scopes
           value: ${scopes()}
      - if:
           test: hasScope('admin')
           language: spel
           flow:
              - template:
                   src: |
                      Only for admins!
                      Caller scopes: ${property.scopes}
              - return: {}
      - if:
           test: hasScope({'finance','accounting'})
           language: spel
           flow:
              - template:
                   src: |
                      Only for finance or accounting!
                      Caller scopes: ${property.scopes}
              - return: {}
      - template:
           src: Normal API
      - return: {}
```
