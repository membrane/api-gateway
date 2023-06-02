### WEBSOCKET INTERCEPTING EXAMPLE

In this example we are going to see how Websocket frames are routed through Membrane Service Proxy. Additionally, 
Membrane Service Proxy logs the content of the WebSocket stream by intercepting and processing it.

WebSocket is a protocol which allows full-duplex communication with a single TCP connection. One can "upgrade" an
HTTP connection by sending specific WebSocket HTTP headers. You will find more information on WebSockets on
https://tools.ietf.org/html/rfc6455


#### PREPARATIONS
1. Download the latest Node.js release at https://github.com/nodejs/node/releases (v7.10.0 as of now) and install it


#### RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Start Membrane Service proxy by running the `service-proxy.[bat|sh]` in this folder
   
2. Look at the console window and wait until "Membrane ... up and running!". This window will remain open
   
3. Start some WebSocket communication by running the `startWebsocket.[bat|sh]`
   
4. Wait until the WebSocket application is done. It will close its window when finished
   
5. Look at the console window of Membrane Service Proxy and observe the content of the WebSocket stream


#### HOW IT IS DONE

The following part describes the example in detail.

Take a look at the proxies.xml.

```
<serviceProxy port="9999">
    <!-- WebSocket intercepting starts here -->
    <webSocket>
        <!-- logs the content of a WebSocket frame to the console  -->
        <wsLog/>
    </webSocket>
    <target port="8080" host="localhost"/>
</serviceProxy>
```

* In it, you will find a service proxy that listens on port `9999`.

* In this service proxy you will find a webSocket element that contains a wsLog element. The webSocket element can read
and write WebSocket frames. When a WebSocket frame is read, it is processed by interceptors. The wsLog element is an
interceptor that just logs the content of the WebSocket frame to the console of Membrane Service Proxy. Additional
interceptors can be defined and added.

* At last the WebSocket frames are sent to a WebSocket server at `ws://localhost:8080`.


#### WEBSOCKETS


The `index.js` and `package.json` files are part of the Node application that does some exemplary WebSocket communication.
This application starts a WebSocket server that listens for incoming WebSocket connections on port `8080`. Additionally,
it starts a WebSocket client that connects to Membrane Service Proxy on port `9999` and then sends a message to the
WebSocket server through Membrane Service Proxy. The server respond to this message and the application closes.

---
See:
- [webSocket](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/webSocket.htm) reference