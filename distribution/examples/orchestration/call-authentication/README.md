# Using Calls for Cookie-Based Login

To simplify client access to secured backends, authentication can be handled internally by the API gateway.

This example shows how to delegate a login step via the `call` plugin to fetch a `SESSION` cookie and inject it automatically.

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