# API Key Authentication and Authorization

Secure endpoints using API keys.

## Running the Sample
***Note:*** *The requests are also available in the requests.http file.*

1. **Navigate** to the `examples/security/api-key/simple` directory.
2. **Start** the API Gateway by executing `membrane.sh` (Linux/Mac) or `membrane.cmd` (Windows).
3. **Test Mandatory API Key Authentication**:
    - Send a request without an API key to see access denial:
      ```
      curl http://localhost:2000 -v
      ```
    - Try an invalid API key:
      ```
      curl http://localhost:2000 -H "X-Api-Key: 98765" -v
      ```
    - Use a valid API key to gain access:
      ```
      curl http://localhost:2000 -H "X-Api-Key: abc123" -v
      ```
    - Alternatively, provide the API key as a query parameter:
      ```
      curl http://localhost:2000?api-key=abc123 -v
      ```

## Understanding the Configuration

### Key Stores
Key stores maintain API keys and their corresponding scopes through various methods. In this instance, we use the simple in-config "keys"-store, which holds the keys inside the `proxies.xml` file.:

```xml
<apiKey>
   <keys>
      <secret value="123" />
      <secret value="456">
         <scope>admin</scope>
      </secret>
   </keys>
</apiKey>
```

### Mandatory API Key Authentication
This configuration enables mandatory API key authentication globally. It defines valid API keys and allows clients to provide their keys either via HTTP headers or query parameters. Upon successful authentication, requests are forwarded to the intended destination.

```xml
<global>
   <apiKey>
      <keys>
         <secret value="demokey" />
         <secret value="aed8bcc4-7c83-44d5-8789-21e4024ac873" />
         <secret value="abc123" />
      </keys>
      <headerExtractor />
      <queryParamExtractor />
   </apiKey>
</global>
```
###  More Complex Examples
See:
- [API Keys with RBAC](./../rbac/README.md)
- [API Keys with OpenAPI](../apikey-openapi) 