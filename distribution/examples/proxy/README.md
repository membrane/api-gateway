###PROXY EXAMPLE

Membrane offers the functionality forward its HTTP requests to a proxy.
Membrane can also serve as an HTTP proxy itself.

This example demonstates both features.


####RUNNING THE EXAMPLE

Using the following URL you can retreive the description of a web service.

http://www.thomas-bayer.com/axis2/services/BLZService?wsdl


To run the example execute the following steps: 

1. Go to the `examples/proxy` directory.

2. Execute `service-proxy.bat`

3. Open the URL http://localhost:2000/axis2/services/BLZService?wsdl in your browser.

4. Open the URL http://localhost:2001/axis2/services/BLZService?wsdl in your browser.


####HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the `proxies.xml` file.


The proxies.xml file defines several `<router>` elements. Each of these elements starts an independent
instance of Membrane router. These instances are started in the order in which they are defined.


The first router demonstates Membrane's capability to act itself as a proxy.


The second router shows how tow to tell Membrane to forward *all* outbound HTTP requests to a proxy.
(In fact, the proxy defined above is used.)


The third router shows how tow to tell Membrane to only forward the HTTP request retreiving the WSDL
to a proxy.
(Normal SOAP requests will be routed directly to the web service's endpoint as defined in the WSDL.
They will *not* use the proxy.)


The fourth router shows all configuration options for the <httpClientConfig /> element: Currently
any of
* proxy and proxy authentication
* basic authentication
* timeouts

can be configured.