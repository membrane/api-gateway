# Padding Header

This guide demonstrates how to use the Padding Header.
The Padding Header is utilized to protect against [breach attacks](https://nvd.nist.gov/vuln/detail/CVE-2013-3587) on HTTPS requests by introducing random padding, which thwarts attackers from exploiting variations in encrypted content length when repeatedly sending identical requests.
## Running the Sample

1. Go to the `examples/security/padding-header` directory.
2. Run `membrane.sh` or `membrane.cmd` to start the API Gateway.
3. Send a request to http://localhost:2000:
```
curl http://localhost:2000 -v
```
4. Examine the X-Padding header in the response, which will look something like this:

```X-Padding: p,@7;#G=:}vbx`#Rd{wqtA0W;FxM1```

5. Repeat the request multiple times and observe how the `X-Padding header changes with each request. 