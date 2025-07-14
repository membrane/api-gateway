# Configuration using the Spring Expression Language

This example shows how to use the **Spring Expression Language (SpEL)** and environment variables to configure the
Membrane API Gateway in a dynamic, flexible way.

For more information on SpEL, see the official documentation:  
[Spring Expression Language Reference](http://docs.spring.io/spring/docs/3.1.x/spring-framework-reference/html/expressions.html)

---

## Files and Structure

All configurations are done in the `proxies.xml` file under:

```
examples/extending-membrane/configuration-properties/
```

This file demonstrates two APIs:

- One API forwards all incoming requests using dynamic configuration values from Spring.
- Another API is protected with basic authentication using a password from an environment variable.

---

## Breakdown of the Config (`proxies.xml`)

```xml
<!-- Load properties from environment variables (JOHNS_PASSWORD_HASH), ignore missing files -->
<context:property-placeholder
        location="file:/non-existent"
        system-properties-mode="ENVIRONMENT"
        ignore-resource-not-found="true"
        ignore-unresolvable="true"/>
```

```xml
<!-- Define properties using Spring util namespace -->
<util:properties id="myProp">
    <spring:prop key="LISTEN_PORT">2000</spring:prop>               <!-- API port for forwarding -->
    <spring:prop key="PATH_RE">true</spring:prop>                   <!-- Enable RegEx path matching -->
    <spring:prop key="DEST_HOST">membrane-soa.org</spring:prop>     <!-- Forward to this host -->
    <spring:prop key="DEST_PORT">80</spring:prop>                   <!-- Forward to this port -->
</util:properties>
```

```xml
<!-- Forwarding API (configured via Spring properties) -->
<api port="#{myProp.LISTEN_PORT}">
    <path isRegExp="#{myProp.PATH_RE}">.*</path>
    <target host="#{myProp.DEST_HOST}" port="#{myProp.DEST_PORT}"/>
</api>
```

```xml
<!-- Protected API with environment variable password -->
<api port="2001">
    <basicAuthentication>
        <user username="john"
              password="${JOHNS_PASSWORD_HASH}"/>
    </basicAuthentication>
    <template>
        This is the protected secret.
    </template>
    <return/>
</api>
```

---

## Running the Example

1. **Navigate to the example directory:**

```bash
cd examples/extending-membrane/configuration-properties/
```

2. **Set the required environment variable:**

> Make sure to quote the hash to avoid Bash interpreting special characters:

```bash
export JOHNS_PASSWORD_HASH='$6$oQ4Zb3JdEWhJFnlb$o8NmTMFGEr4K4qHGSb.JYUQUv6II72dZG6tR6a3R.Vn0qxb0YrNFwgP5f4X19VXve/c6wIpC9LMQ1I/I6s95o0'
```

3. **Start Membrane:**

```bash
./membrane.sh
```

4. **Send a request to the protected endpoint (port 2001):**

```bash
curl -u john:secret http://localhost:2001/
```

You should receive:

```
This is the protected secret.
```

---

## Port Explanation

| Port   | Purpose                                                                                                                                            |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| `2000` | Forwards all incoming traffic to `membrane-soa.org:80`, using dynamic values via Spring expressions.                                               |
| `2001` | Exposes a protected endpoint that requires HTTP Basic Authentication. The password is injected via the `JOHNS_PASSWORD_HASH` environment variable. |

---

## Troubleshooting

| Issue                                                   | Solution                                                                                |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------|
| **401 Unauthorized**                                    | Check that you’re using the correct plaintext password for the given hash.              |
| **Password not replaced**                               | Ensure the environment variable is set *before* launching Membrane.                     |
| **`${JOHNS_PASSWORD_HASH}` appears in logs or browser** | Indicates that the placeholder wasn't resolved. Double-check `export` usage and quotes. |
| **No response in browser**                              | Try forcing a full reload (see table below).                                            |

---

## Force Browser Reload (if caching issues occur)

| Browser | Shortcut                                         |
|---------|--------------------------------------------------|
| Safari  | ALT + click reload or CMD + SHIFT + R (on macOS) |
| Firefox | SHIFT + click reload                             |
| Chrome  | SHIFT + F5 or CONTROL + F5                       |
| IE      | CONTROL + F5                                     |
| Edge    | CONTROL + click reload                           |

