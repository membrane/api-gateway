![Membrane Logo](distribution/media/membrane-logo-m-text.png)

# API Gateway

[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

A versatile **API Gateway** for **REST**, **WebSockets**, and **legacy Web Services**, built in Java.

## Features

### **OpenAPI**

- Deploy APIs directly from [OpenAPI specifications](https://www.membrane-api.io/openapi/configuration-and-validation).
- Validate requests and responses against [OpenAPI](distribution/examples/openapi/validation-simple) and **JSON Schema**.

### **API Security**
- Support for [JSON Web Tokens](#json-web-tokens), [OAuth2](https://www.membrane-soa.org/service-proxy/oauth2-provider-client.html), [API Keys](distribution/examples/api-management), [NTLM](distribution/examples/ntlm), and [Basic Authentication](https://www.membrane-soa.org/api-gateway-doc/current/configuration/reference/basicAuthentication.htm).
- Built-in [OAuth2 Authorization Server](https://www.membrane-soa.org/service-proxy-doc/4.8/security/oauth2/flows/code/index.html).
- Implement **rate limiting** to control traffic ([example](#rate-limiting)).
- Protection for **GraphQL**, **JSON**, and **XML** APIs against malicious inputs.

### **Legacy Web Services**
- Seamless support for [SOAP message routing](#soap-web-services).
- Configure, validate, and rewrite WSDL-based services, including [message validation](#message-validation-against-wsdl-and-xsd).

### **Additional Features**
- Intuitive **Admin Web Console** for monitoring and management.
- Advanced [load balancing](#load-balancing) to ensure high availability.
- Flexible [message transformation](#message-transformation) for seamless data processing.
- Embeddable reverse proxy HTTP framework to build custom API gateways.

# Getting Started

## Java

### Prerequisites
- Ensure **Java 17** or newer is installed.

### Setup and Run
1. **Download and Extract**
  - Get the latest [binary release](https://github.com/membrane/service-proxy/releases).
  - Unzip the downloaded file to a directory of your choice.

2. **Start the Gateway**
  - Open a terminal in the extracted directory.
  - Run the appropriate command for your operating system:
    - **Linux/Mac:** `./service-proxy.sh`
    - **Windows:** `service-proxy.bat`

3. **Access the Gateway**
  - Open your browser and navigate to [http://localhost:2000](http://localhost:2000).
  - The gateway will forward traffic to [https://api.predic8.de](https://api.predic8.de) by default.

4. **Modify Configuration**
  - To customize the behavior, edit the file located at `conf/proxies.xml`.


## Docker

### Quick Start
Run the Membrane API Gateway in a Docker container:
```bash
docker run -p 2000:2000 predic8/membrane
```  

### Access the Gateway
- Open [http://localhost:2000](http://localhost:2000) in your browser, or use `curl`:
  ```bash
  curl http://localhost:2000
  ```  
- The response will match the output of directly calling [https://api.predic8.de](https://api.predic8.de).

### Changing the Configuration
To use a custom [proxies.xml](distribution/router/conf/proxies.xml) configuration file, bind it to the Membrane container.

#### For Windows/Linux:
```bash
docker run -v proxies.xml:/opt/membrane/conf/proxies.xml -p 2000:2000 predic8/membrane
```  

#### For Mac:
```bash
docker run -v "$(pwd)/proxies.xml:/opt/membrane/conf/proxies.xml" -p 2000:2000 predic8/membrane
```  

### Learn More
For detailed Docker setup instructions, see the [Membrane Deployment Guide](https://membrane-api.io/deployment/#docker).


## Next Steps

### Explore and Experiment
- Try the code snippets below.
- Run the provided [examples](distribution/examples#readme) to see Membrane in action.

### Dive into Tutorials
- Follow the [REST API Tutorial](https://membrane-api.io/tutorials/rest/) to learn about deploying and securing RESTful services.
- Check out the [SOAP API Tutorial](https://membrane-api.io/tutorials/soap/) for legacy web service integration.

### Read the Documentation
- For detailed guidance, visit the [official documentation](https://www.membrane-soa.org/service-proxy-doc/).

# Configuration

### Customizing Membrane
To configure Membrane, edit the `proxies.xml` file located in the `conf` folder.

### Using Samples
Explore the sample configurations provided below. Copy and modify them to suit your needs, then save or restart the gateway to apply the changes.

For even more sample have a look at the `examples` folder.


## Simple REST and HTTP Forwarding APIs

### Define an API Route
To forward requests from the API Gateway to a backend, use a simple `api` configuration. The example below routes requests received on port `2000` with a path starting with `/shop` to the backend at `https://api.predic8.de`:

```xml
<api port="2000">
  <path>/shop</path>
  <target url="https://api.predic8.de"/>
</api>
```  

### Testing the Configuration
After adding the configuration to the `proxies.xml` file, open the following URL in your browser to test the API: [http://localhost:2000/shop/v2/](http://localhost:2000/shop/v2/)


## Using OpenAPI for Configuration & Validation

### Deploy APIs with OpenAPI
Membrane allows you to configure APIs directly from OpenAPI documents in the `proxies.xml` file. Backend addresses and other details are automatically derived from the OpenAPI description.

#### Example Configuration
The snippet below shows how to deploy an API using an OpenAPI file (`fruitshop-api.yml`) with request validation enabled:

```xml
<api port="2000">
    <openapi location="fruitshop-api.yml" validateRequests="yes"/>
</api>
```  

#### Viewing Deployed APIs
Once configured, a list of deployed APIs is available at:  

[http://localhost:2000/api-docs](http://localhost:2000/api-docs)

![List of OpenAPI Deployments](distribution/examples/openapi/openapi-proxy/api-overview.png)

Click on an API title in the list to open the Swagger UI for interactive exploration and testing:

![Swagger UI](distribution/examples/openapi/openapi-proxy/swagger-ui.png)

### Learn More
For additional details and a working example, check out the [OpenAPI Example](distribution/examples/openapi).


## Routing

Membrane offers versatile routing options. Its fallthrough mechanism ensures that only the first matching API rule is applied, skipping the rest. This enables precise and efficient routing based on criteria such as paths, HTTP methods, or hostnames.

### Example: Advanced Routing

The configuration below demonstrates several routing rules, with comments explaining their behavior:

```xml
<!-- Block POST requests -->
<api port="2000" method="POST">
    <response>
        <static>POST is blocked!</static>
    </response>
    <return statusCode="405"/>
</api>

<!-- Requests matching "/shop/v2/products/.*" -->
<api port="2000">
    <path isRegExp="true">/shop/v2/products/.*</path>
    <target url="https://api.predic8.de" />
</api>

<!-- All other requests to "/shop" -->
<api port="2000">
    <path>/shop</path>
    <target url="https://api.predic8.de" />
</api>

<!-- Requests with a HOST header of "www.predic8.de" -->
<api port="2000" host="www.predic8.de">
    <response>
        <static>Calling Web Server</static>
    </response>
    <return/>
</api>

<!-- Requests to "api.predic8.de" -->
<api port="2000" host="api.predic8.de">
    <response>
        <static>Calling API</static>
    </response>
    <return/>
</api>
```  

### Configuration Options

- **`port`**: The port Membrane listens on for incoming connections.
- **`method`**: Matches the HTTP method (e.g., `GET`, `POST`, `DELETE`). Use `*` to match any method.
- **`host`**: Specifies hostnames for routing. Supports basic globbing with `*`.
- **`path`**: Matches request paths. Regular expressions can be enabled with `isRegExp="true"`.

For more routing options, see the [Membrane API documentation](https://www.membrane-api.io/docs/current/api.html).

---  

This version adds structure, clear explanations for each rule, and practical use cases for better readability and understanding.

### Short Circuit

Sometimes, you may need an endpoint that doesn’t forward requests to a backend. Membrane makes it easy to create such endpoints.

#### Example: Health Check Endpoint
The following configuration creates a health check endpoint that responds to requests at [http://localhost:2000/health](http://localhost:2000/health):

```xml
<api port="2000">
  <path>/health</path>
  <response>
    <static>I'am fine.</static>
  </response>
  <return statusCode="200"/>
</api>
```

#### Example: Blocking Specific Paths
You can block specific paths (e.g., `/nothing`) while allowing other calls to pass through.

**Routing Note:** APIs are matched from top to bottom. When multiple APIs share the same port, place the APIs with stricter routing conditions higher in the configuration.

```xml
<api port="2000"> <!-- Calls to /nothing are blocked with 404 -->
  <path>/nothing</path>
  <response>
    <static>Nothing to see!</static>
  </response>
  <return statusCode="404"/>
</api>

<api port="2000">
  <response>
    <static>Other call to port 2000</static>
  </response>
  <return statusCode="404"/>
</api>
```

## Scripting

Membrane has powerful scripting features that allow to realize the desired behaviour of an API. You can use the Groovy or the Javascript language to write small plugins. 

### Groovy Scripts

The following API executes a Groovy script during the request and the response. 

```xml
<api port="2000">
    <groovy>
        println "I'am executed in the ${flow} flow"
        println "HTTP Headers:\n${header}"
    </groovy>
    <target url="https://api.predic8.de"/>
</api>
```

After invoking [http://localhost:2000](http://localhost:2000) you can see the following output in the console where you have started Membrane:

```text
I'am executed in the REQUEST flow
HTTP Headers:
Host: localhost:2000
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:133.0) Gecko/20100101 Firefox/133.0
...

I'am executed in the RESPONSE flow
HTTP Headers:
Content-Length: 390
Content-Type: application/json
```

## Message Transformation

### Manipulating HTTP Headers

You can modify HTTP headers in requests or responses using Membrane's `setHeader` and `headerFilter` feature. This is particularly useful for enabling CORS or adding custom headers.

#### Example: Adding CORS Headers
The following configuration adds `CORS` headers to the responses received from the backend:

```xml
<api port="2000">
    <response>
        <setHeader name="Access-Control-Allow-Origin" value="*" />
        <setHeader name="Access-Control-Allow-Methods" value="GET" />
    </response>
    <target url="https://api.predic8.de" />
</api>
```

### Example: Setting Headers from JSON Body Content

Membrane allows dynamic extraction of values from the JSON body of a request or response and uses them to set HTTP headers. 

#### Example Configuration
The following example extracts the `id` and `name` fields from a JSON body and sets them as custom headers in the response:

```xml
<api port="2000">
    <response>
        <!-- Extract the "id" field from the JSON body and set it as the X-Product-Id header -->
        <setHeader name="X-Product-Id" value="${jsonPath('$.id')}"/>
        
        <!-- Extract the "name" field from the JSON body and set it as the X-Product-Name header -->
        <setHeader name="X-Product-Name" value="${jsonPath('$.name')}"/>
    </response>
    <target url="https://api.predic8.de" />
</api>  
```

### Removing HTTP Headers

You can easily remove specific HTTP headers from requests or responses (or both) using the `headerFilter` element. This is useful for cleaning up headers or meeting security requirements.

#### Example: Header Filtering
The following configuration demonstrates how to manage headers:

```xml
<api port="2000">
  <response>
  <headerFilter>
    <include>X-XSS-Protection</include> <!-- Keep the X-XSS-Protection header -->
    <exclude>X-.*</exclude>             <!-- Remove all headers starting with "X-" except those explicitly included -->
  </headerFilter>
  </response>
  <target url="https://www.predic8.de"/>
</api>
```  

- **`<include>`:** Specifies headers to retain.
- **`<exclude>`:** Defines headers to remove. Wildcards can be used for patterns.

The first matching rule will be acted upon by the filter.

### Create JSON from Query Parameters

```xml
<api port="2000" method="GET">
  <request>
    <template contentType="application/json" pretty="yes">
      { "answer": ${params.answer} }
    </template>
  </request>
  <return/>
</api>
```

Call this API with `http://localhost:2000?answer=42` . Replace `<return.../>` with your `<target url="backend-server"/>`.

## Transform JSON into TEXT, JSON or XML with Templates

Call the following APIs with this request:

```
curl -d '{"city":"Berlin"}' -H "Content-Type: application/json" "http://localhost:2000"
```

This template will transform the JSON input into plain text:

```xml

<api port="2000" method="POST">
    <request>
        <template contentType="text/plain">
            City: ${json.city}
        </template>
    </request>
    <return statusCode="200"/>
</api>
```

...into JSON:

```xml

<template contentType="application/json" pretty="true">
    {
    "destination": "${json.city}"
    }
</template>
```

...and into XML:

```xml

<template contentType="application/xml">
    <![CDATA[
    <places>
        <place>${json.city}</place>
    </places>
    ]]>
</template>
```

### Transform XML into Text or JSON

Using the `xpathExtractor` you can extract values from XML request or response bodies and store it in properties. The properties are then available as variables in the `template`
plugin.

```xml

<api port="2000">
    <request>
        <xpathExtractor>
            <property name="fn" xpath="person/@firstname"/>
        </xpathExtractor>
        <template>Buenas Noches, ${fn}sito!</template>
    </request>
    <return statusCode="200" contentType="text/plain"/>
</api>
```

See: [message-transformation examples](./distribution/examples/message-transformation)

## Complex Transformations using Javascript or Groovy

Use the Javascript or Groovy plugin for more powerful yet simple transformations.

```xml

<api port="2000">
    <request>
        <javascript>
            ({ id:7, place: json.city })
        </javascript>
    </request>
    <return contentType="application/json"/>
</api>
```

Call the API with this curl command:

```
curl -d '{"city":"Berlin"}' -H "Content-Type: application/json" "http://localhost:2000"
```

## Transformation with Computations

This script transforms the input and adds some calculations.

```xml

<api port="2000">
    <request>
        <javascript>

            function convertDate(d) {
            return d.getFullYear() + "-" + ("0"+(d.getMonth()+1)).slice(-2) + "-" + ("0"+d.getDate()).slice(-2);
            }

            ({
            id: json.id,
            date: convertDate(new Date(json.date)),
            client: json.customer,
            total: json.items.map(i => i.quantity * i.price).reduce((a,b) => a+b),
            positions: json.items.map(i => ({
            pieces: i.quantity,
            price: i.price,
            article: i.description
            }))
            })
        </javascript>
    </request>
    <return/>
</api>
```

See [examples/javascript](distribution/examples/javascript) for a detailed explanation. The same transformation can also be realized with [Groovy](distribution/examples/groovy)

## Beautifier

You can beautify a JSON or XML using the `<beautifier/>` plugin.

```xml

<api port="2000">
    <template contentType="application/xml"><![CDATA[
        <foo><bar>baz</bar></foo>
    ]]></template>

    <beautifier/>

    <return statusCode="200"/>
</api>
```  

Returns:

```xml

<foo>
    <bar>baz</bar>
</foo>
```

# Branching and Conditionals

Replace `5XX` error messages from a backend:
```xml

<api port="2000">
  <response>
    <if test="statusCode matches '5\d\d'" language="SpEL">
      <static>
        Error!
      </static>
    </if>
  </response>
  <return/>
</api>
```

Check if certain scopes/roles are provided:
```xml

<api port="2000">
    <if test="hasScopes({'admin', 'webmaster'})" language="SpEL">
      <target url="https://localhost:2000/admin" />
    </if>
    <target host="localhost" port="1001" />
</api>
```

# Writing Extensions with Groovy or Javascript

Dynamically manipulate and monitor messages with Groovy:

```xml

<api port="2000">
    <response>
        <groovy>
            header.add("X-Groovy", "Hello from Groovy!")
            println("Status: ${message.statusCode}")
            CONTINUE
        </groovy>
    </response>
    <target url="https://api.predic8.de"/>
</api>
```

Create a response with Javascript:

```xml

<api port="2000">
    <response>
        <javascript>
            var body = JSON.stringify({
            foo: 7,
            bar: 42
            });

            Response.ok(body).contentType("application/json").build();
        </javascript>
    </response>
    <return/> <!-- Do not forward, return immediately -->
</api>
```

Also try the [Groovy](distribution/examples/groovy) and [Javascript example](distribution/examples/javascript).

# Security

Membrane offers lots of security features to protect backend servers.

## API Keys

Secure any API using a simple API key configuration like this:

```xml
<api port="2000">
    <apiKey>
        <keys>
            <secret value="demokey123" />
        </keys>
        <headerExtractor />
    </apiKey>
    <static>Hidden API</static>
    <return/>
</api>
```

This will fetch the API key from the "X-Api-Key" header if present.
On incorrect key entry or missing key, access is denied and an error response is sent.
For more complex configurations using RBAC and file-based key stores see: [API Key Plugin Examples](./distribution/examples/security/api-key/rbac/README.md)

## JSON Web Tokens

The API below only allows requests with valid tokens from Microsoft's Azure AD. You can also use the JWT validator for other identity providers.

```xml

<api port="8080">
    <jwtAuth expectedAud="api://2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0">
        <jwks jwksUris="https://login.microsoftonline.com/common/discovery/keys"/>
    </jwtAuth>
    <target url="https://your-backend"/>
</api>
```

## OAuth2

### Secure an API with OAuth2

Use OAuth2/OpenID to secure endpoints against Google, Azure AD, GitHub, Keycloak or Membrane authentication servers.

```xml

<api port="2001">
    <oauth2Resource>
        <membrane src="https://accounts.google.com"
                  clientId="INSERT_CLIENT_ID"
                  clientSecret="INSERT_CLIENT_SECRET"
                  scope="email profile"
                  subject="sub"/>
    </oauth2Resource>
    <groovy>
        // Get email from OAuth2 and forward it to the backend
        def oauth2 = exc.properties.oauth2
        header.setValue('X-EMAIL',oauth2.userinfo.email)
        CONTINUE
    </groovy>
    <target url="https://backend"/>
</api>
```

Try the tutorial [OAuth2 with external OpenID Providers](https://membrane-soa.org/api-gateway-doc/current/oauth2-openid.html)

### Membrane as Authorization Server

Operate your own identity provider:

```xml

<api port="2000">
    <oauth2authserver location="logindialog" issuer="http://localhost:2000" consentFile="consentFile.json">
        <staticUserDataProvider>
            <user username="john" password="password" email="john@predic8.de"/>
        </staticUserDataProvider>
        <staticClientList>
            <client clientId="abc" clientSecret="def" callbackUrl="http://localhost:2001/oauth2callback"/>
        </staticClientList>
        <bearerToken/>
        <claims value="aud email iss sub username">
            <scope id="username" claims="username"/>
            <scope id="profile" claims="username email password"/>
        </claims>
    </oauth2authserver>
</api>
```

See the [OAuth2 Authorization Server](https://www.membrane-soa.org/service-proxy-doc/4.8/oauth2-code-flow-example.html) example.

## Basic Authentication

```xml

<api port="2000">
    <basicAuthentication>
        <user name="bob" password="secret"/>
    </basicAuthentication>
    <target host="localhost" port="8080"/>
</api>
```

## SSL/TLS

Route to SSL/TLS secured endpoints:

```xml

<api port="8080">
    <target url="https://api.predic8.de"/>
</api>
```

Secure endpoints with SSL/TLS:

```xml

<api port="443">
  <ssl>
    <keystore location="membrane.p12" password="secret" keyPassword="secret" />
    <truststore location="membrane.p12" password="secret" />
  </ssl>
  <target host="localhost" port="8080"  />
</api>
```

## Rate Limiting

Limit the number of incoming requests:

```xml

<api port="2000">
    <rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
    <target host="localhost" port="8080"/>
</api>
```

# Load balancing

Distribute workload to multiple backend nodes. [See the example](distribution/examples/loadbalancing)

```xml

<api port="8080">
    <balancer name="balancer">
        <clusters>
            <cluster name="Default">
                <node host="my.backend-1" port="4000"/>
                <node host="my.backend-2" port="4000"/>
                <node host="my.backend-3" port="4000"/>
            </cluster>
        </clusters>
    </balancer>
</api>
```

# Rewrite URLs

```xml

<api port="2000">
    <rewriter>
        <map from="^/good-looking-path/(.*)" to="/backend-path/$1"/>
    </rewriter>
    <target host="my.backend.server"/>
</api>
```

# Log HTTP

Log data about requests and responses to a file or [database](distribution/examples/logging/jdbc-database) as [CSV](distribution/examples/logging/csv)
or [JSON](distribution/examples/logging/json) file.

```xml

<api port="2000">
    <log/> <!-- Logs to the console -->
    <statisticsCSV file="./log.csv"/> <!-- Logs fine-grained CSV -->
    <target url="https://api.predic8.de"/>
</api>
```

# Websockets

Route and intercept WebSocket traffic:

```xml

<api port="2000">
    <webSocket url="http://my.websocket.server:1234">
        <wsLog/>
    </webSocket>
    <target port="8080" host="localhost"/>
</api>
```

See [documentation](https://www.membrane-soa.org/service-proxy-doc/4.8/websocket-routing-intercepting.html)

# SOAP Web Services

Integrate legacy services.

## API configuration from WSDL

SOAP proxies configure themselves by analysing WSDL:

```xml

<soapProxy wsdl="http://thomas-bayer.com/axis2/services/BLZService?wsdl"/>
```

## Message Validation against WSDL and XSD

The _validator_ checks SOAP messages against a WSDL document including referenced XSD schemas.

```xml

<soapProxy wsdl="http://thomas-bayer.com/axis2/services/BLZService?wsdl">
    <validator/>
</soapProxy>
```


# Operation

## Instrumentation

### OpenTelemetry Integration
Membrane supports integration with **OpenTelemetry** traces using the `openTelemetry` plugin and the `W3C` propagation standard. This enables detailed tracing of requests across Membrane and backend services.

![OpenTelemetry Example](distribution/examples/opentelemetry/resources/otel_example.png)  
This diagram illustrates Membrane in a tracing setup with a backend service and a database connection.

#### Example Setup
The configuration below shows Membrane forwarding requests to a backend, while exporting OpenTelemetry data to a collector:

```xml
<api port="2000">
    <openTelemetry sampleRate="1.0">
        <otlpExporter host="localhost" port="4317"/>
    </openTelemetry>
    <target host="localhost" port="3000"/>
</api>
```  

For a working example and detailed setup, see the [OpenTelemetry Example](./distribution/examples/opentelemetry).



