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

1. Download the latest ActiveMQ Classic release at https://activemq.apache.org/components/classic/download/ and unpack it
   
2. Navigate to the `bin` folder of ActiveMQ in a console
   
3. Start ActiveMQ by running `activemq start` in the console. This window will remain open



#### RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Start Membrane by running `membrane.[sh|cmd]` in this folder
   
2. Look at the console window and wait until `Membrane ... up and running!`. This window will remain open
   
3. Open http://localhost:9998
   
4. Observe a message appearing on the website
   
5. Look at the console window of Membrane and observe the changed `STOMP` message


#### HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the `apis.yaml` file.

```yaml
api:
  port: 9998
  flow:
    - webSocket:
        url: http://localhost:61614/
        flow:
          - wsStompReassembler:
              flow:
                - groovy:
                    src: |
                      def method = exc.getRequest().getMethod();
                      def header = exc.getRequest().getHeader();
                      def body = exc.getRequest().getBodyAsStringDecoded();
                      if(exc.getRequest().getMethod() == "SEND")
                      body = "[MEMBRANE]: " + exc.getRequest().getBodyAsStringDecoded();
                      exc.setRequest(new Request.Builder().method(method).header(header).body(body).build());
          - wsLog: {}
  target:
    host: localhost
    port: 9999
```

* In it, you will find an API that listens on port `9998`.
* The `webSocket` plugin allows the upgrade to the WebSocket protocol and sends WebSocket connections to ActiveMQ on
port `61614`. Its flow contains the `wsStompReassembler` and `wsLog` plugins.
* The `wsStompReassembler` plugin wraps a STOMP message in a Membrane exchange for further processing.
* The `wsLog` plugin just logs the content of the WebSocket frame to the console of Membrane.
* All other requests go to the target, a second API on port `9999` that serves the website for this example.

The `wsStompReassembler` runs regular Membrane plugins on a STOMP message. There are some limitations:
 1. only the request of an exchange is used ( in both directions )
 2. only method, header and body are set
 3. the target cannot be changed and is defined by the enclosing webSocket element.   

In this example a `groovy` plugin is run on the `STOMP` over `WebSocket` message. The plugin changes the content of
the message by prepending a prefix to the body of the message.

---
See:
- [webSocket](https://membrane-api.io/docs/current/webSocket.html) reference
- [wsStompReassembler](https://membrane-api.io/docs/current/wsStompReassembler.html) reference