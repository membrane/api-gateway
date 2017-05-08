WEBSOCKET STOMP EXAMPLE

In this example we are going to see how STOMP-over-WebSocket messages are routed through Membrane Service Proxy.
Additionally regular interceptors can be run on those messages (with limitations) in addition to all WebSocket
interceptors.

STOMP is an abbreviation for Streaming Text Oriented Messaging Protocol. It's a text based protocol for messaging,
supported by ActiveMQ. You will find more information on STOMP on https://stomp.github.io/stomp-specification-1.2.html

WebSocket is another protocol which allows full-duplex communication with a single TCP connection. One can "upgrade" a
HTTP connection by sending specific WebSocket HTTP headers. You will find more information on WebSockets on
https://tools.ietf.org/html/rfc6455




PREPARATIONS

1. download the latest ActiveMQ release at https://github.com/apache/activemq/releases (v5.14.5 as of now)
2. navigate to the bin/activemq folder in a console
3. start ActiveMQ by running "activemq start" in the console. This window will remain open




RUNNING THE EXAMPLE

To run the example execute the following steps:

1. start Membrane Service proxy by running the service-proxy.[bat|sh]
2. look at the console window and wait until "Membrane ... up and running!". This window will remain open
3. open http://localhost:4443
4. observe a message appearing on the website
5. look at the console window of Membrane Service Proxy and observe the changed STOMP message




HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the proxies.xml file.

[...]
<serviceProxy port="4443">
    [...]
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
    <target host="localhost" port="4444"/>
    [...]
</serviceProxy>
[...]

In it you will find a service proxy that listens on port 4443.
In this service proxy you will find a webSocket element that contains wsStompReassembler and wsLog elements. The
webSocket element can read and write WebSocket frames and has ActiveMQ on port 61614 as a target. The wsStompReassembler
element wraps a STOMP message in a Membrane-typical exchange for further processing. The wsLog element is an interceptor
that just logs the content of the WebSocket frame to the console of Membrane Service Proxy.
The last element of the service proxy is a target element that points to a web server from Membrane Service Proxy
hosting a website for this example.

The wsStompReassembler runs typical Membrane Service Proxy interceptors on a STOMP message. There are some limitations:
 (1) only the request of an exchange is used ( in both directions )
 (2) only method, header and body are set
 (3) the target cannot be changed and is defined by the enclosing webSocket element
In this example a groovy interceptor is run on the STOMP over WebSocket message. The interceptor changes the content of
the message by prepending a prefix to the body of the message.