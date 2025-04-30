# Using  Calls for APIs

This example demonstrates how to chain API calls and dynamically extract data from a JSON response using the `jsonpath` expression language and property substitution.

### **How It Works**

**Port 2000:** Public entrypoint.
1. Sends a request to retrieve the latest product (`sort=id&order=desc&limit=1`).
2. Extracts the product `id` from the JSON response using JSONPath (`$.products[0].id`).
3. Uses that `id` to make a second call and fetch full product details.

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

   Should return e.g.:
   ```
   HTTP/1.1 200 OK
   ...
    {
      "id": 19,
      "name": "Apple",
      "price": 5.49,
      "modified_at": "2025-04-30T04:42:02.548087Z"
    }
   ```

3. **Check `proxies.xml`** to see how JSONPath and property injection are used to enable dynamic routing.
