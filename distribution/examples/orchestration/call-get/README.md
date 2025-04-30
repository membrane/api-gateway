# Orchestration: Calling an API with GET

To provide simpler interfaces for clients you can create APIs that are calling multiple endpoints for each invocation.

The example illustrates how to chain API calls and dynamically extract data from a JSON response using the `jsonpath` expression language and property substitution.

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

### **How It Works**

1. The API sends a request to retrieve the latest product (`sort=id&order=desc&limit=1`).
2. Then it extracts the product `id` from the JSON response using JSONPath (`$.products[0].id`).
3. Finally it uses that `id` to make a second call and fetch full product details.

**Check `proxies.xml`** to see how JSONPath and property injection are used to enable dynamic routing.