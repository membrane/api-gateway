# Orchestrating Authentication

To simplify client access to secured backends, authentication can be handled transparently by the API gateway.

The gateway can orchestrate various authentication methods—Basic Auth, API keys, OAuth2, or, as in this example, cookie-based authentication.

This example demonstrates how to delegate the login step using the `call` plugin to retrieve a `SESSION` cookie and inject it automatically into subsequent requests.

### **Running the Example**

1. **Start the Router**
   ```sh
   ./membrane.sh  # Linux/Mac  
   membrane.bat   # Windows  
   ```

2. **Test the flow:**
   ```sh
   curl -i http://localhost:2000
   ```

   Should return:
   ```
   HTTP/1.1 200 OK
 
   Success!
   ```

Check `proxies.xml` to see the `<call>` + cookie logic. 