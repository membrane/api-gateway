# Orchestration: Aggregating Product Names and Prices

Client applications often need to consolidate data that’s spread across multiple endpoints into a single, easy-to-consume payload. 

This example retrieves an initial list of items, then for each item performs a detail lookup and merges selected fields (for example, name and price) into one unified response.

### **Running the Example**

1. **Start the Router**
   ```sh
   ./membrane.sh    # Linux/Mac  
   membrane.bat     # Windows  
   ```

2. **Invoke the API**
   ```sh
   curl -i http://localhost:2000
   ```

### **How It Works**

1. **Fetch All Products**  
   The first `<call>` retrieves up to 1000 products from the upstream service:
   ```
   https://api.predic8.de/shop/v2/products?limit=1000
   ```
2. **Store the Array**  
   We capture the full array of products in the `products` property using JSONPath:
   ```xml
   <setProperty name="products" value="${$.products}" language="jsonpath"/>
   ```
3. **Iterate and Enrich**  
   The `<for>` loop walks through each item in `properties.products`:
    - **Detail Call**: Fetches full details for the current product by its `id`.
    - **Extract Price**: Uses JSONPath to grab the `price` field.
    - **Inject Price**: A small Groovy snippet writes that price back into the `it` object.
4. **Render Final JSON**  
   The `<template>` builds a response containing only `name` and `price` for each enriched product:

Check **proxies.xml** to see exactly how JSONPath, property substitution, `<for>` looping, and Groovy injection work together to orchestrate these calls.