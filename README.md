Membrane Service Proxy
======================

Check the [Repository at GitHub](https://github.com/membrane/service-proxy) for the latest source code. Read the [CHANGELOG](https://github.com/membrane/service-proxy/blob/master/distribution/router/CHANGELOG.txt) for recent changes.

What is Membrane?
-----------------
Membrane Service Proxy is an open source, reverse HTTP proxy framework written in Java, licensed under ASF 2.0, that can be used as

*   a Service Virtualization layer,
*   an API Gateway,
*   a synchronous ESB for HTTP based Integration,
*   a Security Proxy.

To get started, follow the [SOAP](http://membrane-soa.org/esb-doc/current/soap-quickstart.htm) and [REST](http://membrane-soa.org/esb-doc/current/rest-quickstart.htm) tutorials or have a look at the [examples](http://membrane-soa.org/esb-doc/current/interceptors/examples.htm) or the [FAQ](https://github.com/membrane/service-proxy/wiki/Membrane-Service-Proxy-FAQ).

Get Started
-----------

Download the [binary](http://membrane-soa.org/downloads/http-router.htm).

Unpack.

Start `service-proxy.sh` or `service-proxy.bat`.

Have a look at the main configuration file `conf/proxies.xml`. Changes to this file are instantly deployed.

Run the samples in the examples folder or go to the [website](http://membrane-soa.org/service-proxy/) for more documentation.

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
