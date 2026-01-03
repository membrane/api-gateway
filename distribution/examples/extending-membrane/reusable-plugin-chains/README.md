# Reusable Plugin Chains

This example demonstrates how shared chains helps standardize both request and response handling while letting each API define its own behavior.  A chain groups plugins and interceptors into reusable components, significantly reducing redundancy and the overall size of your configuration, especially when managing multiple APIs.

## **Running the Example**

1. **Start the Router**
   ```sh
   ./router-service.sh  # Linux/Mac  
   router-service.bat   # Windows  
   ```
2. **Test the APIs:**
    - **API 1:**
      ```sh
      curl -i http://localhost:2000/foo
      ```
      Observe the gateway log output for 'Path: ...'
    - **API 2:**
      ```sh
      curl -i http://localhost:2000/bar
      ``` 
      Observe the gateway output and the response HTTP headers
3. **Check `apis.yaml`** to see how chains are applied.
