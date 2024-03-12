# API Key Authentication and Authorization

Secure endpoints using API keys combined with role-based access control (RBAC).

## Running the Sample
***Note:*** *The requests are also available in the requests.http file.*

1. **Navigate** to the `examples/security/api-key` directory.
2. **Start** the API Gateway by executing `service-proxy.sh` (Linux/Mac) or `service-proxy.bat` (Windows).
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
      curl http://localhost:2000 -H "X-Api-Key: P8MBR" -v
      ```
    - Alternatively, provide the API key as a query parameter:
      ```
      curl http://localhost:2000/?api-key=P8MBR -v
      ```
4. **Test Optional API Key with RBAC**:
    - Access with a non-admin scope key to receive limited access:
      ```
      curl http://localhost:3000 -H "X-Key: 123456789" -v
      ```
    - Use an admin scope key for admin access:
      ```
      curl http://localhost:3000 -H "X-Key: key_321_abc" -v
      ```
    - Make a request without any key to see default behavior:
      ```
      curl http://localhost:3000 -v
      ```

## Understanding the Configuration

### File Stores
File stores maintain API keys and their corresponding scopes through various methods. In this instance, we use the ApiKeyFileStore, which holds the keys within the local file system. File stores can either be globally registered for all apiKey plugins via a Spring Bean declaration or configured locally by embedding them directly within the apiKey plugin:

```xml
<spring:bean class="com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore">
    <spring:property name="location" value="./demo-keys.txt" />
</spring:bean>
```
**OR**
```xml
<apiKey>
    ...
    <keyFileStore location="./demo-keys.txt" />
</apiKey>
```

**DECLARING API KEYS IN THE CONFIG**

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
This part of the configuration sets up an API on port `2000`, where providing an API key is mandatory. The setup allows API keys to be received either as HTTP headers or query parameters.  
On successful authentication we simply return with the message "Secret Area!". Here we would direct the request to the destination server.

```xml
<api port="2000">
    <apiKey>
        <headerExtractor />
        <queryParamExtractor />
    </apiKey>
    <template>Hidden API</template>
    <return/>
</api>
```
###  Optional API Key with RBAC
The configuration for port `3000` involves optional API key authentication with additional checks for user roles or scopes, as well as employing a custom header for API key extraction.  
By using the conditional "if" plugin, we can check and validate provided scopes using built-in functions.

```xml
<api port="3000">
    <apiKey required="false">
        <headerExtractor name="X-Key" />
    </apiKey>

   <if test="hasScope('admin')" language="SpEL">
      <template>Only for admins!</template>
      <return />
   </if>

   <if test="hasScope({'finance','accounting'})" language="SpEL">
      <template>Only for finance or accounting!</template>
      <return />
   </if>

    <template>Normal API</template>
    <return />
</api>
```
