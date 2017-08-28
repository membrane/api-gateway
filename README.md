Membrane Service Proxy
======================
[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

Reverse HTTP proxy framework written in Java, that can be used

*   as an API Gateway
*   for HTTP based integration
*   as a security proxy
*   as a WebSockets and STOMP router


To get started, follow the [REST](https://www.membrane-soa.org/service-proxy-doc/4.4/rest-quickstart.htm) and [SOAP](https://www.membrane-soa.org/service-proxy-doc/4.4/soap-quickstart.htm) tutorials, have a look at the [examples](https://github.com/membrane/service-proxy/tree/master/distribution/examples) or the [FAQ](https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ).

Get Started
-----------

Download the [binary](https://github.com/membrane/service-proxy/releases).

Unpack.

Start `service-proxy.sh` or `service-proxy.bat`.

Have a look at the main configuration file `conf/proxies.xml`. Changes to this file are instantly deployed.

Run the samples in the examples directory or go to the [website](http://membrane-soa.org/service-proxy/) for more documentation.

Samples
-------

### REST

Hosting virtual REST services is easy:
```xml
<serviceProxy port="80">
    <path>/restnames/</path>
    <target host="www.thomas-bayer.com" />
</serviceProxy>
```

### SOAP

SOAP proxies configure themselves by analysing WSDL:
```xml
<soapProxy wsdl="http://thomas-bayer.com/axis2/services/BLZService?wsdl">
</soapProxy>
```

Add features like logging or XML Schema validation against a WSDL document:
```xml
<soapProxy wsdl="http://thomas-bayer.com/axis2/services/BLZService?wsdl">
	<validator />
	<log />
</soapProxy>
```

### Monitoring and manipulation

Dynamically manipulate and monitor messages with Groovy and JavaScript (Nashorn): 

```xml
<serviceProxy port="2000">
  	<groovy>
    	exc.request.header.add("X-Groovy", "Hello from Groovy")
    	CONTINUE
  	</groovy>
	<target host="membrane-soa.org" port="80" />
</serviceProxy>
```
```xml
<serviceProxy port="2000">
  	<javascript>
    	exc.getRequest().getHeader().add("X-Javascript", "Hello from JavaScript");
   		CONTINUE;
  	</javascript>
	<target host="membrane-soa.org" port="80" />
</serviceProxy>
```

Route and intercept WebSocket traffic:
```xml
<serviceProxy port="2000">
        <webSocket url="http://my.websocket.server:1234">
            <wsLog/>
        </webSocket>
    <target port="8080" host="localhost"/>
</serviceProxy>
```
(_Find an example on [membrane-soa.org](http://www.membrane-soa.org/service-proxy-doc/4.4/websocket-routing-intercepting.htm)_)

Limit the number of requests in a given time frame:
```xml
<serviceProxy port="2000">
    <rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
    <target host="www.google.de" port="80" />
</serviceProxy>
```
Rewrite URLs:
```xml
<serviceProxy port="2000">
    <rewriter>
    	<map from="^/goodlookingpath/(.*)" to="/backendpath/$1" />
    </rewriter>
    <target host="my.backend.server" port="80" />
</serviceProxy>
```

Monitor HTTP traffic:
```xml
<serviceProxy port="2000">
    <log/>
    <target host="membrane-soa.org" port="80" />
</serviceProxy>
```

### Security

Use the widely adopted OAuth2/OpenID Framework to secure endpoints:
```xml
<serviceProxy name="Resource Service" port="2001">
    <oauth2Resource>
        <membrane src="https://accounts.google.com" clientId="INSERT_CLIENT_ID" clientSecret="INSERT_CLIENT_SECRET" scope="email profile" subject="sub"/>
    </oauth2Resource>    
    <groovy>
        def oauth2 = exc.properties.oauth2
        exc.request.header.setValue('X-EMAIL',oauth2.userinfo.email)
        CONTINUE
    </groovy>
    <target host="thomas-bayer.com" port="80"/>
</serviceProxy>
```
(_Find an example on [membrane-soa.org](http://www.membrane-soa.org/service-proxy-doc/4.4/oauth2-openid.htm)_)

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
(_Find an example on [membrane-soa.org](http://www.membrane-soa.org/service-proxy-doc/4.4/oauth2-code-flow-example.htm)_)

Secure an endpoint with basic authentication: 
```xml
<serviceProxy port="2000">
    <basicAuthentication>
        <user name="bob" password="secret" />
    </basicAuthentication>
    <target host="www.thomas-bayer.com" port="80" />
</serviceProxy>
```

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
	<target host="www.predic8.de" />
</serviceProxy>
```

Limit the number of incoming requests:
```xml
<serviceProxy port="2000">
    <rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
    <target host="www.predic8.de" port="80" />
</serviceProxy>
```

### Clustering

Distribute your workload to multiple nodes:
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
