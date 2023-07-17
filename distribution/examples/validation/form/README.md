# Validation - Forms

This sample explains how to set up and use the `formValidation` plugin.


## Running the Example

1. Navigate to the `<membrane-root>/examples/validation/form` directory.

2. Execute the `service-proxy.sh` script, or its batch file equivalent.

3. Use the following `curl` command in a terminal to send valid form data to Membrane:  
   `curl -o /dev/null -s -w "%{http_code}\n" -X POST -d "name=JohnSmith" http://localhost:2000`  
   The command returns the status code 200 "Ok", indicating that the request was successful.

4. Now send some invalid data to see how the system responds. Run the following curl command, which includes digits in the form data (making it invalid as per the rules defined in the proxies.xml file):
   `curl -o /dev/null -s -w "%{http_code}\n" -X POST -d "name=JohnSmith1nv4l1d" http://localhost:2000`  
   This time, we receive the status code 400, denoting a "Bad Request".

## How it is done

Let's examine the `proxies.xml` file.

```xml
<router>
  <api port="2000">
    <formValidation>
      <field name="name" regex="[a-zA-Z]+" />
    </formValidation>
    <target url="http://www.thomas-bayer.com" />
  </api>
</router>
```

We define an `<api>` component on port 2000 that uses the `formValidation` plugin. By adding <field /> child elements to the plugin, we establish the necessary validation rules for the form. Each field specified is associated with a regex pattern that acts as the validation rule.

---
See:
- []() reference