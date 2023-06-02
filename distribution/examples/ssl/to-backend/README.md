# Routing to Backends over TLS/SSL 


## Running the Example

In the example we will route to an SSL protected API.

1. Execute `service-proxy.sh` or `service-proxy.bat`

2. Open the URL http://localhost:2000 in your browser.

3. Have a look at the `proxies.xml` file.

    ```
    <serviceProxy port="2000">
      <target host="api.predic8.de" port="443">
        <ssl/>
      </target>
    </serviceProxy>
    ```

---
See:
- [ssl](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/ssl.htm) reference 