Membrane API Gateway
======================
[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

Open Source API Gateway written in Java that supports REST APIs, WebSockets, STOMP and legacy Web Services. Featuring:

**API Security:**
* Authentification with [OAuth2](), [API Keys]() and [Basic Auth]() 
* [OAuth2 Authorization server]() 
* Rate Limiting
* XML Protection


**OpenAPI:**
* Deployment of [OpenAPI](distribution/examples/openapi) documents as APIs
*  [Message validation](distribution/examples/openapi/openapi-validation-simple) against OpenAPI

**Legacy Web Services:**
* SOAP Message Routing
* WSDL configuration, [message Validation](#legacy-soap-and-xml-web-services) and [WSDL rewritting]()

**Other:**
* Admin Web console
* Load balancing
* Embeddable reverse proxy HTTP framework for own API Gateways and products



Get Started
-----------

1. Download the [binary](https://github.com/membrane/service-proxy/releases) and unzip it.

3. Run `service-proxy.sh` or `service-proxy.bat` in a terminal.

4. Look at the configuration `conf/proxies.xml` and change to your needs.

Run the [samples](distribution/examples#readme), follow the [REST](https://www.membrane-soa.org/service-proxy-doc/4.8/rest-quickstart.htm) or [SOAP](https://www.membrane-soa.org/service-proxy-doc/4.4/soap-quickstart.htm) tutorial, see the [Documentation](https://www.membrane-soa.org/service-proxy-doc/) or the [FAQ](https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ).

Configuration
-------

Try the following snippets by copying them into the `conf/proxies.xml` file.

## REST

Routing requests from port `8080` to `api.predic8.de` when the path starts with `/foo`. 

```xml
<serviceProxy port="8080">
  <path>/foo</path>
  <target host="api.predic8.de" port="80" />
</serviceProxy>
```

### OpenAPI Configuration & Validation

Configures APIs from OpenAPI documents and validates messages against it. [more...](distribution/examples/openapi)

```xml
<router>
  <openAPIProxy port="2000">
    <spec location="https://petstore3.swagger.io/api/v3/openapi.json"
          validateRequests="yes"/>
  </openAPIProxy>
</router>
```

### Monitoring and Message Manipulation using Groovy or Javascript

Dynamically manipulate and monitor messages with Groovy:

```xml
<serviceProxy port="2000">
  <groovy>
    exc.request.header.add("X-Groovy", "Hello from Groovy")
    CONTINUE
  </groovy>
  <target host="localhost" port="8080" />
</serviceProxy>
```

or Javascript(Nashorn):

```xml
<serviceProxy port="2000">
  <javascript>
    exc.getRequest().getHeader().add("X-Javascript", "Hello from JavaScript");
    CONTINUE;
  </javascript>
  <target host="localhost" port="8080" />
</serviceProxy>
```

Try also the [Groovy example](distribution/examples/groovy) and [Javascript Example](distribution/examples/javascript).

### Rewrite URLs for Hypermedia

```xml
<serviceProxy port="2000">
    <rewriter>
    	<map from="^/goodlookingpath/(.*)" to="/backendpath/$1" />
    </rewriter>
    <target host="my.backend.server" port="80" />
</serviceProxy>
```

### Log HTTP
```xml
<serviceProxy port="2000">
    <log/>
    <target host="localhost" port="8080" />
</serviceProxy>
```

# Websockets

Route and intercept WebSocket traffic:

```xml
<serviceProxy port="2000">
  <webSocket url="http://my.websocket.server:1234">
    <wsLog/>
  </webSocket>
  <target port="8080" host="localhost"/>
</serviceProxy>
```
(_Find an example on [membrane-soa.org](https://www.membrane-soa.org/service-proxy-doc/4.8/websocket-routing-intercepting.htm)_)

# Security

## OAuth2 

### Secure an API with OAuth2

Use the widely adopted OAuth2/OpenID Framework to secure endpoints against Google, Azure AD, github, Keycloak or Membrane authentication servers.

```xml
<serviceProxy name="Resource Service" port="2001">
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
</serviceProxy>
```
(_Find an example on [membrane-soa.org](https://www.membrane-soa.org/service-proxy-doc/4.8/oauth2-openid.htm)_)

### Membrane as AuthorizationServer/Identity Provider

Operate your own OAuth2/OpenID AuthorizationServer/Identity Provider:
```xml
<serviceProxy name="Authorization Server" port="2000">
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
</serviceProxy>
```
(_Find an example on [membrane-soa.org](https://www.membrane-soa.org/service-proxy-doc/4.8/oauth2-code-flow-example.htm)_)

## Basic Authentication

Secure an endpoint with basic authentication:
```xml
<serviceProxy port="2000">
    <basicAuthentication>
        <user name="bob" password="secret" />
    </basicAuthentication>
    <target host="localhost" port="8080" />
</serviceProxy>
```

## SSL/TLS

Route to SSL/TLS secured endpoints:
```xml
<serviceProxy port="8080">
  <target host="www.predic8.de" port="443">
    <ssl/>
  </target>
</serviceProxy>
```

Secure endpoints with SSL/TLS:
```xml
<serviceProxy port="443">
  <ssl>
    <keystore location="membrane.jks" password="secret" keyPassword="secret" />
    <truststore location="membrane.jks" password="secret" />
  </ssl>
  <target host="localhost" port="8080"  />
</serviceProxy>
```

## Rate Limiting

Limit the number of incoming requests:

```xml
<serviceProxy port="2000">
  <rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
  <target host="localhost" port="8080" />
</serviceProxy>
```

# Loadbalancing

Distribute workload to multiple backend nodes. 
```xml
<serviceProxy name="Balancer" port="8080">
  <balancer name="balancer">
    <clusters>
      <cluster name="Default">
        <node host="my.backend.service-1" port="4000"/>
        <node host="my.backend.service-2" port="4000"/>
        <node host="my.backend.service-3" port="4000"/>
      </cluster>
    </clusters>
  </balancer>
</serviceProxy>
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
