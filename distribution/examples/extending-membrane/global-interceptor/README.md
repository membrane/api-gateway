# Global Interceptor

This example demonstrates how to use global interceptors to apply plugins for request and response processing, such as authentication, modifications, logging, and transformations, across all APIs. Global interceptors ensure consistent behavior, reduce redundancy, and simplify configuration.
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
3. **Check `proxies.xml`** to see how the global interceptors are applied.
