### STOMP OVER WEBSOCKET INTERCEPTING

In this example we are going to see how STOMP-over-WebSocket messages are routed through Membrane API Gateway.
Additionally, regular interceptors can be run on those messages (with limitations) in addition to all WebSocket
interceptors.

`STOMP` is an abbreviation for Streaming Text Oriented Messaging Protocol. It's a text based protocol for messaging,
supported by ActiveMQ. You will find more information on STOMP on https://stomp.github.io/stomp-specification-1.2.html

`WebSocket` is another protocol which allows full-duplex communication with a single TCP connection. One can "upgrade" an
HTTP connection by sending specific WebSocket HTTP headers. You will find more information on WebSockets on
https://tools.ietf.org/html/rfc6455


#### PREPARATIONS

1. Download the latest ActiveMQ-release at https://github.com/apache/activemq/releases (v5.14.5 as of now)
   
2. Navigate to the `bin/activemq` folder in a console
   
3. Start ActiveMQ by running `activemq start` in the console. This window will remain open



#### RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Start Membrane Service proxy by running the `service-proxy.[bat|sh]` in this folder
   
2. Look at the console window and wait until `Membrane ... up and running!`. This window will remain open
   
3. Open http://localhost:9998
   
4. Observe a message appearing on the website
   
5. Look at the console window of Membrane Service Proxy and observe the changed `STOMP` message


#### HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the proxies.xml file.

```
<serviceProxy port="9998">
  <!-- Membrane does not support WebSocket Extensions for now, so we remove the header -->
  <groovy>
    if(exc.getRequest() != null)
    exc.getRequest().getHeader().removeFields("Sec-WebSocket-Extensions");
    if(exc.getResponse() != null)
    exc.getResponse().getHeader().removeFields("Sec-WebSocket-Extensions");
  </groovy>

  <!-- WebSocket intercepting starts here -->
  <webSocket url="http://localhost:61614/">
    <!-- the wsStompReassembler take a STOMP over WebSocket frame and constructs an exchange from it -->
    <wsStompReassembler>
      <!-- modify the exchange to have a "[MEMBRANE]:" prefix -->
      <groovy>
        def method = exc.getRequest().getMethod();
        def header = exc.getRequest().getHeader();
        def body = exc.getRequest().getBodyAsStringDecoded();
        if(exc.getRequest().getMethod() == "SEND")
        body = "[MEMBRANE]: " + exc.getRequest().getBodyAsStringDecoded();
        exc.setRequest(new Request.Builder().method(method).header(header).body(body).build());
      </groovy>
    </wsStompReassembler>
    <!-- logs the content of a WebSocket frame to the console  -->
    <wsLog/>
  </webSocket>
  <target host="localhost" port="9999"/>
</serviceProxy>
```

* In it, you will find a service proxy that listens on port `9998`. 
* In this service proxy you will find a webSocket element that contains `<wsStompReassembler>` and `<wsLog>` elements. The
webSocket element can read and write WebSocket frames and has ActiveMQ on port `61614` as a target.
* The `<wsStompReassembler>` element wraps a STOMP message in a Membrane-typical exchange for further processing.
* The `<wsLog>` element is an interceptor that just logs the content of the WebSocket frame to the console of Membrane Service Proxy.
The last element of the service proxy is a target element that points to a web server from Membrane Service Proxy
hosting a website for this example.

The `<wsStompReassembler>` runs typical Membrane Service Proxy interceptors on a STOMP message. There are some limitations:
 1. only the request of an exchange is used ( in both directions )
 2. only method, header and body are set
 3. the target cannot be changed and is defined by the enclosing webSocket element.   

In this example a groovy interceptor is run on the `STOMP` over `WebSocket` message. The interceptor changes the content of
the message by prepending a prefix to the body of the message.

---
See:
- [webSocket](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/webSocket.htm) reference
- [wsStompReassembler](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/wsStompReassembler.htm) reference