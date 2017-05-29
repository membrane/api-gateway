Membrane Service Proxy
======================
[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

Reverse HTTP proxy framework written in Java, licensed under ASF 2.0, that can be used

*   as an API Gateway
*   for HTTP based integration
*   as a security proxy
*   as a WebSockets and STOMP router


To get started, follow the [REST](http://membrane-soa.org/esb-doc/current/rest-quickstart.htm) and [SOAP](http://membrane-soa.org/esb-doc/current/soap-quickstart.htm) tutorials or have a look at the [examples](http://membrane-soa.org/esb-doc/current/interceptors/examples.htm) or the [FAQ](https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ).

Get Started
-----------

Download the [binary](https://github.com/membrane/service-proxy/releases).

Unpack.

Start `service-proxy.sh` or `service-proxy.bat`.

Have a look at the main configuration file `conf/proxies.xml`. Changes to this file are instantly deployed.

Run the samples in the examples directory or go to the [website](http://membrane-soa.org/service-proxy/) for more documentation.

Samples
-------

Hosting virtual REST services is easy:
```xml
<serviceProxy port="80">
    <path>/restnames/</path>
    <target host="www.thomas-bayer.com" />
</serviceProxy>
```

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
