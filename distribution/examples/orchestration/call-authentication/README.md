# Using Calls for login

To simplify client access to secured backends, authentication can be handled internally by the API gateway.

This example demonstrates how to delegate that login step using the `call` plugin to fetch and inject an API key automatically.

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
 
   Secured backend!
   ```

### **How It Works**

- **Port 2000:** Public entrypoint. Calls an internal service (port 3000) to fetch an API key, then forwards the request to a protected backend (port 3001).
- **Port 3000:** Simulates an authentication service. Returns an `X-Api-Key` header with a static value.
- **Port 3001:** Backend protected by API key. Access is only granted if the correct key (`ABCDE`) is present in the request header.

**Check `proxies.xml`** to see how the call and API key logic is configured.