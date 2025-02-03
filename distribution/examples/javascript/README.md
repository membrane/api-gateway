# Access and Manipulate Messages with Javascript - Example

JavaScript is a powerful tool for manipulating messages and customizing the behavior of an API. With the `javascript` plugin, you can embed JavaScript directly into API definitions to achieve flexible transformations and logic.

**Note:** These examples require Membrane version 5.1.0 or newer.

## Running the Example

1. Review the [proxies.xml](proxies.xml) file to see how the APIs are configured with the `javascript` plugin.
2. Open a command-line session or terminal.
3. Run `service-proxy.ps1` (Windows) or `./service-proxy.sh` (Linux/Mac) in this folder.
4. Open a second terminal and execute the following commands:

### Create JSON with JavaScript

The query parameter from:

```bash
curl "http://localhost:2000?id=7&city=Berlin" 
```

will be filled in a JSON document. 

```json
{"id":"7","city":"Berlin"}
```

See API at port 2000 in 'proxies.xml' for details.

### Transform JSON to JSON

Examine the [order.json](order.json) file to understand the input format. Then, send it to the API to transform it into a different JSON structure:

```bash
cat order.json

curl -d @order.json -H "Content-Type: application/json" http://localhost:2010
```

### Access HTTP Headers and a Spring Bean

Send a request to the API and inspect the response:

```bash
curl http://localhost:2020 -v
> GET / HTTP/1.1
> Host: localhost:2020

< HTTP/1.1 200 Ok
< Content-Length: 21
< X-Javascript: 42

Greatings from Javascript       
```

The response should include an `X-Javascript` header. Additionally, the console output of `service-proxy.sh` or `service-proxy.ps1` will display the request header fields logged by the script:

```
Request headers:
Host: localhost:2000
User-Agent: curl/7.79.1
Accept: */*
X-Forwarded-For: 127.0.0.1
X-Forwarded-Proto: http
X-Forwarded-Host: localhost:2000
```

---
See: 
- [javascript plugin](https://www.membrane-soa.org/service-proxy-doc/current/configuration/reference/javascript.htm) reference







