# Global Chain

Some functionalities, such as authentication and rate limiting, are required across all APIs, not just individually. By adding plugins to the global interceptor chain rather than configuring them for each API separately, these functionalities become universally active for all APIs. Global interceptors ensure consistent behavior, eliminate redundancy, and simplify configuration.

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
      **Check the request:** both contain CORS headers
3. **Check `proxies.xml`** to see how the global interceptors are applied.
