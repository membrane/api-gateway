# Membrane API Gateway

[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

API Gateway for REST, WebSockets and legacy Web Services written in Java. Featuring:

**OpenAPI:**
* API [deployment from OpenAPI](https://membrane-api.io/openapi/) documents
*  [Message validation](distribution/examples/openapi/validation-simple) against OpenAPI

**API Security:**
* [JSON Web Tokens](#json-web-tokens)
* [OAuth2](https://www.membrane-soa.org/service-proxy/oauth2-provider-client.htm), [API Keys](distribution/examples/api-management), [NTLM](distribution/examples/ntlm) and [Basic Authentication](https://www.membrane-soa.org/api-gateway/current/configuration/reference/basicAuthentication.htm) 
* [OAuth2 authorization server](https://www.membrane-soa.org/service-proxy-doc/4.8/security/oauth2/flows/code/index.htm) 
* [Rate limiting](#rate-limiting)
* GraphQL, JSON and XML protection

**Legacy Web Services:**
* [SOAP message routing](#soap-web-services)
* WSDL configuration, [message validation](#message-validation-against-wsdl-and-xsd) and rewriting

**Other:**
* Admin Web console
* [Load balancing](#load-balancing)
* [Message Transformation](#message-transformation)
* Embeddable reverse proxy HTTP framework for own API gateways



# Getting Started

1. Download the [binary](https://github.com/membrane/service-proxy/releases) and unzip it

3. Run `service-proxy.sh` or `service-proxy.bat` in a terminal

4. Change the configuration `conf/proxies.xml`

Run the [samples](distribution/examples#readme), follow the [REST](https://membrane-api.io/tutorials/rest/) or [SOAP](https://membrane-api.io/tutorials/soap/) tutorial, see the [documentation](https://www.membrane-soa.org/service-proxy-doc/) or the [FAQ](https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ).

# Configuration

Try the following snippets by copying them into the `conf/proxies.xml` file.

## Using OpenAPI for Configuration & Validation

Configures APIs from OpenAPI  and validates messages against the definitions. Needed data like backend addresses are taken from the OpenAPI description.  [more...](distribution/examples/openapi)

This configuration is all you need to deploy from OpenAPI:
```xml
<api port="2000">
    <openapi location="fruitshop-api.yml" validateRequests="yes"/>
</api>
```

A list of deployed APIs if available at `http://localhost:2000/api-doc`

![List of OpenAPI Deployments](distribution/examples/openapi/openapi-proxy/api-overview.png)

Click on the API title to get the Swagger UI.

![Swagger UI](distribution/examples/openapi/openapi-proxy/swagger-ui.png)

## REST and HTTP APIs

Routing requests from port `2000` to `api.predic8.de` when the path starts with `/shop`. 

```xml
<api port="2000">
    <path>/shop</path>
    <target url="https://api.predic8.de"/>
</api>
```

Call the API by opening `http://localhost:2000/shop` in the browser.


# Message Transformation

## Create JSON from Query Parameters

```xml
<api port="2000" method="GET">
    <request>
        <template contentType="application/json" pretty="yes">
            { "answer": ${params.answer} }
        </template>
    </request>
    <return statusCode="200"/>
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

Use this one to transform into JSON:

```xml
<template contentType="application/json" pretty="true">
    {
        "destination": "${json.city}"
    }
</template>
```

and that into XML:

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

Using the `xpathExtractor` you can extract values from XML request or response bodies and store it in properties. The properties are then available as variables in the `template` plugin.

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

## JSON Web Tokens

The API below only allows requests with valid tokens from Microsoft's Azure AD. You can also use the JWT token validator for other identity providers.

```xml
<api port="8080">
    <jwtAuth expectedAud="api://2axxxx16-xxxx-xxxx-xxxx-faxxxxxxxxf0">
        <jwks jwksUris="https://login.microsoftonline.com/common/discovery/keys" />
    </jwtAuth>
    <target url="https://your-backend"/>
</api>
```

## OAuth2 

### Secure an API with OAuth2

Use OAuth2/OpenID to secure endpoints against Google, Azure AD, github, Keycloak or Membrane authentication servers.

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
Try the tutorial [OAuth2 with external OpenID Providers](https://membrane-soa.org/api-gateway-doc/current/oauth2-openid.htm)

### Membrane as Authorization Server

Operate your own identity provider:
```xml
<api port="2000">
  <oauth2authserver location="logindialog" issuer="http://localhost:2000" consentFile="consentFile.json">
    <staticUserDataProvider>
      <user username="john" password="password" email="john@predic8.de" />
    </staticUserDataProvider>
    <staticClientList>
      <client clientId="abc" clientSecret="def" callbackUrl="http://localhost:2001/oauth2callback" />
    </staticClientList>
    <bearerToken/>
    <claims value="aud email iss sub username">
      <scope id="username" claims="username"/>
      <scope id="profile" claims="username email password"/>
    </claims>
  </oauth2authserver>
</api>
```
See the [OAuth2 Authorization Server](https://www.membrane-soa.org/service-proxy-doc/4.8/oauth2-code-flow-example.htm) example.

## Basic Authentication

```xml
<api port="2000">
    <basicAuthentication>
        <user name="bob" password="secret" />
    </basicAuthentication>
    <target host="localhost" port="8080" />
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
    <keystore location="membrane.jks" password="secret" keyPassword="secret" />
    <truststore location="membrane.jks" password="secret" />
  </ssl>
  <target host="localhost" port="8080"  />
</api>
```

## Rate Limiting

Limit the number of incoming requests:

```xml
<api port="2000">
  <rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
  <target host="localhost" port="8080" />
</api>
```

# Load balancing

Distribute workload to multiple backend nodes. [more ...](distribution/examples/loadbalancing)
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
    	<map from="^/good-looking-path/(.*)" to="/backend-path/$1" />
    </rewriter>
    <target host="my.backend.server"/>
</api>
```

# Log HTTP

Log data about requests and responses to a file or [database](distribution/examples/logging/jdbc-database) as [CSV](distribution/examples/logging/csv) or [JSON](distribution/examples/logging/json) file.

```xml
<api port="2000">
  <log/> <!-- Logs to the console -->
  <statisticsCSV file="./log.csv" /> <!-- Logs fine-grained CSV --> 
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
See [documentation](https://www.membrane-soa.org/service-proxy-doc/4.8/websocket-routing-intercepting.htm)


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
  <validator />
</soapProxy>
```

See [configuration reference](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/) for much more.
