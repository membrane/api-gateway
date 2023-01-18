Membrane API Gateway
======================
[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

Open Source API Gateway written in Java for REST APIs, WebSockets, STOMP and legacy Web Services. Featuring:

**API Security:**
* Authentification with [OAuth2](https://www.membrane-soa.org/service-proxy/oauth2-provider-client.htm), [API Keys](distribution/examples/api-management), [NTLM](distribution/examples/ntlm) and [Basic Authentication](https://www.membrane-soa.org/service-proxy-doc/4.4/configuration/reference/basicAuthentication.htm) 
* [OAuth2 Authorization server](https://www.membrane-soa.org/service-proxy-doc/4.8/security/oauth2/flows/code/index.htm) 
* Rate Limiting
* XML Protection


**OpenAPI:**
* Deployment of [OpenAPI](distribution/examples/openapi) documents as APIs
*  [Message validation](distribution/examples/openapi/openapi-validation-simple) against OpenAPI

**Legacy Web Services:**
* SOAP Message Routing
* WSDL configuration, [message Validation](#legacy-soap-and-xml-web-services) and WSDL rewritting

**Other:**
* Admin Web console
* Load balancing
* Embeddable reverse proxy HTTP framework for own API Gateways and products



Get Started
-----------

1. Download the [binary](https://github.com/membrane/service-proxy/releases) and unzip it.

3. Run `service-proxy.sh` or `service-proxy.bat` in a terminal.

4. Look at the configuration `conf/proxies.xml` and change to your needs.

Run the [samples](distribution/examples#readme), follow the [REST](https://membrane-api.io/tutorials/rest/) or [SOAP](https://membrane-api.io/tutorials/soap/) tutorial, see the [Documentation](https://www.membrane-soa.org/service-proxy-doc/) or the [FAQ](https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ).

Configuration
-------

Try the following snippets by copying them into the `conf/proxies.xml` file.

## REST

Routing requests from port `8080` to `api.predic8.de` when the path starts with `/foo`. 

```xml
<api port="8080">
  <path>/shop</path>
  <target host="api.predic8.de" port="80" />
</api>
```

### OpenAPI Configuration & Validation

Configures APIs from OpenAPI documents and validates messages against it. [more...](distribution/examples/openapi)

```xml
<api port="2000">
    <openapi location="fruitshop-api.yml" validateRequests="yes"/>
</api>
```

### Monitoring and Message Manipulation using Groovy or Javascript

Dynamically manipulate and monitor messages with Groovy:

```xml
<api port="2000">
  <groovy>
    exc.request.header.add("X-Groovy", "Hello from Groovy")
    CONTINUE
  </groovy>
  <target host="localhost" port="8080" />
</api>
```

or Javascript:

```xml
<api port="2000">
  <javascript>
    exc.getRequest().getHeader().add("X-Javascript", "Hello from JavaScript");
    CONTINUE;
  </javascript>
  <target host="localhost" port="8080" />
</api>
```

Try also the [Groovy example](distribution/examples/groovy) and [Javascript Example](distribution/examples/javascript).

### Rewrite URLs for Hypermedia

```xml
<api port="2000">
    <rewriter>
    	<map from="^/goodlookingpath/(.*)" to="/backendpath/$1" />
    </rewriter>
    <target host="my.backend.server" port="80" />
</api>
```

### Log HTTP

Log data about requests and responses to a file or [database](distribution/examples/logging/jdbc-database) as [CSV](distribution/examples/logging/csv) or [JSON](distribution/examples/logging/json) file.

```xml
<api port="2000">
  <log/> <!-- Logs to the console -->
  <statisticsCSV file="./log.csv" /> <!-- Logs finegrained CSV --> 
  <target host="api.predic8.de">
    <ssl/>
  </target>
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
(_Find an example on [membrane-soa.org](https://www.membrane-soa.org/service-proxy-doc/4.8/websocket-routing-intercepting.htm)_)

# Security

## OAuth2 

### Secure an API with OAuth2

Use the widely adopted OAuth2/OpenID Framework to secure endpoints against Google, Azure AD, github, Keycloak or Membrane authentication servers.

```xml
<api name="Resource Service" port="2001">
  <oauth2Resource>
    <membrane src="https://accounts.google.com" clientId="INSERT_CLIENT_ID" clientSecret="INSERT_CLIENT_SECRET" scope="email profile" subject="sub"/>
  </oauth2Resource>    
  <groovy>
    // Get email from OAuth2 and forward it to the backend
    def oauth2 = exc.properties.oauth2
    exc.request.header.setValue('X-EMAIL',oauth2.userinfo.email)
    CONTINUE
  </groovy>
  <target host="thomas-bayer.com" port="80"/>
</api>
```
(_Find an example on [membrane-soa.org](https://www.membrane-soa.org/service-proxy-doc/4.8/oauth2-openid.htm)_)

### Membrane as AuthorizationServer/Identity Provider

Operate your own OAuth2/OpenID AuthorizationServer/Identity Provider:
```xml
<api name="Authorization Server" port="2000">
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
(_Find an example on [membrane-soa.org](https://www.membrane-soa.org/service-proxy-doc/4.8/oauth2-code-flow-example.htm)_)

## Basic Authentication

Secure an endpoint with basic authentication:
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
  <target host="www.predic8.de" port="443">
    <ssl/>
  </target>
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

# Loadbalancing

Distribute workload to multiple backend nodes. [more ...](distribution/examples/loadbalancing)
```xml
<api name="Balancer" port="8080">
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

# Legacy SOAP and XML Web Services

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

See [configuration reference](https://www.membrane-soa.org/service-proxy-doc/4.8/configuration/reference/) for much more.
