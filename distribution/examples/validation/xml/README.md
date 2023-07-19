### Validation - XML

This sample explains how to set up and use the `validator` plugin, utilizing XML schemas for validation.


## Running the Example

1. Go to the directory `<membrane-root>/examples/validation/xml`.


2. Start `service-proxy.bat` or `service-proxy.sh`.


3. Run `curl -d @year.xml http://localhost:2000/`. Observe that you get a successful response.


4. Run `curl -d @invalid-year.xml http://localhost:2000/`. Observe that you get a validation error response.

## How it is done

Let's examine  the `proxies.xml` file.

```xml
<router>
  <api port="2000">
    <request>
      <validator schema="year.xsd" />
    </request>
    <response>
      <validator schema="amount.xsd" />
    </response>
    <target host="localhost" port="2001" />
  </api>
    
  <api port="2001">
    <groovy>
      Response.ok("&lt;amount&gt;100&lt;/amount&gt;").build()
    </groovy>
  </api>
</router>
```

We have two `<api>` components in action, operating on ports `2000` and `2001`.  
The initial one employs the XML schema in the `<validator />` component's schema attribute for validating requests. Upon successful validation, these requests are forwarded to the second `<api>` component.  
Here, an XML document is generated and redirected back to the first `<api>` component for a secondary round of validation, this time using a different schema for response validation.

---
See:
- [validator](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/validator.htm) reference