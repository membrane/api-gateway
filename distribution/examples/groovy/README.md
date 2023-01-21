# Access and manipulate messages with Groovy Scripts - Example

Using the `groovy` element you can run Groovy scripts to manipulate or monitor messages.


## Running the example

Execute the following steps:

1. Open a commandline session or a terminal.
2. Run `service-proxy.bat` or `./service-proxy.sh` in this folder
2. Open a secound terminal and run:

```shell
curl localhost:2000 -v
```

You should get the following output:

```
> GET / HTTP/1.1
> Host: localhost:2000
> User-Agent: curl/7.79.1
> Accept: */*
> 
< HTTP/1.1 200 Ok
< Server: Membrane Service Proxy 5.0.0. See http://membrane-soa.org
< Content-Length: 21
< X-Groovy: 42

Greatings from Spring                              
```

The response body and the `X-Groovy` header were produced by Groovy scripts.

3. Take a look at the output of the `service-proxy.sh/bat` script. You should see the output from a Groovy script, printing the request header fields.

```
Request headers:
Host: localhost:2000
User-Agent: curl/7.79.1
Accept: */*
X-Forwarded-For: 127.0.0.1
X-Forwarded-Proto: http
X-Forwarded-Host: localhost:2000
```

4. Take a look at the configuration file `proxies.xml`. There you'll find the Groovy scripts together with commends explaing the details.

## Troubleshooting

Have a look at the FAQ-Section https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ