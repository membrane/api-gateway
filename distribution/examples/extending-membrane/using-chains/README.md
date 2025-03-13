# **Using Chains**

### **Overview**
This example demonstrates how a shared chain standardizes request handling while allowing each API to define its own response.

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

---

### **Configuration Overview**

#### **Shared Chain (`foo`)**
- Adds a request header (`Foo: Bar`)
- Logs requests
- Sets a default response

```xml
<chainDef id="foo">
    <request>
        <setHeader name="Foo" value="Bar" />
    </request>
    <log />
    <template>Response set by chain</template>
</chainDef>
```

#### **APIs Using the Chain**
```xml
<api port="2000">
    <chain id="foo" />
    <return statusCode="200"/>
</api>

<api port="2001">
    <chain id="foo" />
    <return statusCode="404"/>
</api>
```
