# Global Interceptor

This example demonstrates how to use **global interceptors** to apply modifications, logging, and transformations **to all APIs**. Global interceptors ensure consistent request and response handling across all API rules, reducing redundancy and simplifying configuration.

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
