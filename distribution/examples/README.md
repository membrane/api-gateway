# Working Examples

## Deployment

| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [docker](docker)                                                       | How to create a Membrane docker image.                                                                                                                                 |
| [embedding-java](embedding-java)                                       | How to embed Membrane into Java applications.                                                                                                                          |

## Basic

| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [logging](logging)                                                     | How to log requests and responses into a file and database in text, CSV and JSON format.                                                                               |
| [template](template)                                                   | With the Template Plugin you can intercept request and responses and change them using templates.                                                                      |
| [groovy](scripting/groovy)                                                       | Run groovy scripts to manipulate or monitor messages.                                                                                                                  |
| [internalproxy](internalproxy)                                         | Internally handled endpoint / chainable proxy service.                                                                                                                 |
| [javascript](scripting/javascript)                                               | Manipulate and monitor messages with Javascript.                                                                                                                       |
| [message-transformation](message-transformation)                       | Transformation of messages between JSON, XML and other formats                                                                                                         |
| [rewriter](rewriter)                                                   | With the `RewriteInterceptor` you can rewrite URLs using regular expressions.                                                                                          |

## Security

| Example                                                                | Description                                         |
|------------------------------------------------------------------------|-----------------------------------------------------|
| [acl](security/acl)                                                             | Restrict access to APIs by ip address or hostnames. |                                                                                                                    |
| [basic-auth](security/basic-auth)                                      | _HTTP Basic Authentication_ to secure APIs or Web pages with username and password.                                                                                    |
| [json-protection](security/json-protection)                            | Validate JSON documents and limit document attributes.                                                                                                                 |
| [login page](security/login)                                                    | Protect Web pages using a login page and passwords.                                                                                                                    |
| [ntlm](security/ntlm)                                                           | Authentication against NTLM protected backends e.g. running on Microsoft IIS                                                                                           |
| [oauth2](security/oauth2)                                                       | Please follow https://www.membrane-soa.org/service-proxy-doc/current/oauth2-password-flow-sample.htm                                                                   |
| [openapi](openapi)                                                     | Load APIs from OpenAPI documents and validate requests and responses against OpenAPI.                                                                                  |                                                                 |
| [rateLimiter](security/rateLimiter)                                             | The `RateLimiter` limits the number of requests in a given interval.                                                                                                   |
| [SSL/TLS](security/ssl)                                                         | SSL for APIs and for the communication to backends                                                                                                                     |
| [throttle](throttle)                                                   | With the `ThrottleInterceptor` you can delay and limit parallel requests.                                                                                              |
| [validation](validation)                                               | Contains examples about form, xml schema, schematron, json-schema and soap proxy validation                                                                            |

## XML


| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [xslt](xml/xslt)                                                           | XSLTInterceptor applying XSLT stylesheets to request and response.                                                                                                     |
| [cbr](routing/cbr)                                                             | Content based routing using XPath.                                                                                                                                     |
| [basic-xml-interceptor](xml/basic-xml-interceptor)                         | BasicXmlInterceptor adds date element with current time information inside bar element.                                                                                |
| [stax-interceptor](xml/stax-interceptor)                                   | In this example we will install an interceptor called StaxConverterInterceptor that changes tag name from `<foo>` to `<bar>` using Java STAX API.                      |
| [versioning](versioning)                                               | Examples showcasing routing and xslt versioning                                                                                                                        |


## Web Sockets

| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [stomp-over-websocket-intercepting](web-sockets/stomp-over-websocket-intercepting) | In this example we are going to see how STOMP-over-WebSocket messages are routed through Membrane Service Proxy.                                                       |
| [websocket-intercepting](web-sockets/websocket-intercepting)                       | This example demonstrates how Websocket frames are routed through Membrane Service Proxy.                                                                              |
| [websocket-stomp](web-sockets/websocket-stomp)                                     | STOMP over WebSocket, all handled by Membrane Service Proxy.                                                                                                           |



## Web Services, SOAP and WSDL

| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [add-soap-header](web-services/soap/add-soap-header)                                | This interceptor adds a SOAP header to the incoming request using the Java DOM API.                                                                                    |
| [rest2soap](rest2soap)                                                 | With the REST2SOAP Converter you can make a SOAP Web Service as a REST resource accessible.                                                                            |
| [rest2soap-json](rest2soap-json)                                       | `REST2SOAP` converter exposes a SOAP Web Service as a REST resource. In contrast to the rest2soap example the response will be a JSON object.                          |


## Internals

| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [custom-interceptor](internal/custom-interceptor)                               | How to write custom interceptors in Java.                                                                                                                              |
| [custom-websocket-interceptor](web-sockets/custom-websocket-interceptor)           | Please visit https://www.membrane-soa.org/service-proxy-doc/current/websockets/create-websocket-interceptor.htm to learn how to create custom WebSocket interceptors.  |
| [file-exchangestore](file-exchangestore)                               | Membrane uses exchange stores to save requests and responses on disc or memory.                                                                                        |


## Misc


| Example                                                                | Description                                                                                                                                                            |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [api-management](api-management)                                       | A simple file based api management approach.                                                                                                                           |
| [loadbalancing](loadbalancing)                                         | From simple static laodbalancers to dynamic node management                                                                                                            |
| [service-discovery-with-etcd](service-discovery-with-etcd)             | The publisher publishes endpoint details for services to "the cloud". The configurator reads those details from "the cloud" and dynamically forwards to those services |
| [spel](scripting/spel)                                                           | Using the Spring Expression language as part of router configuration.                                                                                                  |

