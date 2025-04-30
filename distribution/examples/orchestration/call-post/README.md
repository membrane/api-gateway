# Using Calls to Modify and POST API Data

To automate backend interactions, it's often useful to fetch, transform, and forward data between services without involving the client.

This example shows how to fetch a product, modify its data (e.g. price), and post the transformed product back to the API using dynamic property handling and templating.


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

### **How It Works**

1. Fetches product with ID `14`.
2. Extracts the `name` and `price` from the response (`$.name`, `$.price`).
3. Increments the price by 1.
4. Builds a new JSON body using the modified values.
5. Sends a `POST` request to create a new product with the updated data.

**Check `proxies.xml`** to see the configuration.