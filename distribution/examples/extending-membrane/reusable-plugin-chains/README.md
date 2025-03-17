# Reusable Plugin Chains

This example demonstrates how using a shared chain helps standardize both request and response handling while letting each API define its own behavior.  Chains group plugins and interceptors into reusable components, significantly reducing redundancy and the overall size of your proxies.xml configuration, especially when managing multiple APIs.

### **Running the Example**
1. **Start the Router**
   ```sh
   ./router-service.sh  # Linux/Mac  
   router-service.bat   # Windows  
   ```
2. **Test the APIs:**
    - **API 1 (Port 2000) → Returns `200 OK`**
      ```sh
      curl -i http://localhost:2000
      ```
    - **API 2 (Port 2001) → Returns `404 Not Found`**
      ```sh
      curl -i http://localhost:2001
      ```  
3. **Check `proxies.xml`** to see how chains are applied.
