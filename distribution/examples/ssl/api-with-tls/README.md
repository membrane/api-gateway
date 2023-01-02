# Protecting APIs with SSL/TLS

This example describes how to secure an API with SSL/TLS.  


## Running the Example

1. Run `service-proxy.bat` or `service-proxy.sh`
2. Open the following URL in your browser. Please do not forget to use `https` instead of `http`.

    `https://localhost/`

    You should get a warning that the certificate is not trustworthy. Here you can ignore the warning. In production you should use your own certificates.

3. You can also access the API using `curl`. The option `-k` suppresses the check for self-signed certificates.

```                                                                                                    
curl -k -v https://localhost/
```

The output should look like this:

```
*   Trying 127.0.0.1:443...
* Connected to localhost (127.0.0.1) port 443 (#0)
* ALPN, offering h2
* ALPN, offering http/1.1
* successfully set certificate verify locations:
*  CAfile: /etc/ssl/cert.pem
*  CApath: none
* (304) (OUT), TLS handshake, Client hello (1):
* (304) (IN), TLS handshake, Server hello (2):
* (304) (IN), TLS handshake, Unknown (8):
* (304) (IN), TLS handshake, Certificate (11):
* (304) (IN), TLS handshake, CERT verify (15):
* (304) (IN), TLS handshake, Finished (20):
* (304) (OUT), TLS handshake, Finished (20):
* SSL connection using TLSv1.3 / AEAD-CHACHA20-POLY1305-SHA256
* ALPN, server did not agree to a protocol
* Server certificate:
*  subject: CN=membrane
*  start date: Aug  5 10:41:09 2015 GMT
*  expire date: Aug  2 10:41:09 2025 GMT
*  issuer: CN=membrane
*  SSL certificate verify result: self signed certificate (18), continuing anyway.
> GET / HTTP/1.1
> Host: localhost
> 
< HTTP/1.1 200 Ok
< Content-Type: application/json
< 
{
"success" : truet
}
```

## Configuration

Just put a SSL element into a proxy. See the [documentation](https://www.membrane-soa.org/service-proxy-doc/4.4/configuration/reference/ssl.htm).

```xml
<serviceProxy port="443">
  <ssl>
    <keystore location="<<your keystore>>" password="<<your pwd>>" keyPassword="<<your pwd>>" />
    <truststore location="<<your truststore>>" password="<<your pwd>>" />
  </ssl>
  <target host="localhost" port="2000"/>
</serviceProxy>
```