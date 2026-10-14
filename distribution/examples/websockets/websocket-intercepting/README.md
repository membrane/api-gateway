### WEBSOCKET INTERCEPTING EXAMPLE

In this example we are going to see how WebSocket frames are routed through Membrane API Gateway. Additionally,
Membrane logs the content of the WebSocket stream by intercepting and processing it.

WebSocket is a protocol which allows full-duplex communication with a single TCP connection. One can "upgrade" an
HTTP connection by sending specific WebSocket HTTP headers. You will find more information on WebSockets on
https://tools.ietf.org/html/rfc6455


#### PREPARATIONS
1. Download the latest Node.js release at https://github.com/nodejs/node/releases and install it


#### RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Start Membrane by running `membrane.[sh|cmd]` in this folder
   
2. Look at the console window and wait until "Membrane ... up and running!". This window will remain open
   
3. Start some WebSocket communication by running `startWebsocket.[sh|ps1]`
   
4. Wait until the WebSocket application prints `Finished`
   
5. Look at the console window of Membrane and observe the content of the WebSocket stream


#### HOW IT IS DONE

The following part describes the example in detail.

Take a look at the `apis.yaml`.

```yaml
api:
  port: 9999
  flow:
    - webSocket:
        flow:
          - wsLog: {}
  target:
    host: localhost
    port: 8080
```

* In it, you will find an API that listens on port `9999`.

* The `webSocket` plugin allows the upgrade to the WebSocket protocol. Without it, Membrane denies the upgrade. Its
`flow` contains the `wsLog` plugin. Every WebSocket frame is processed by the plugins in this flow. `wsLog` just logs
the content of the frame to the console of Membrane. Additional plugins can be added.

* At last the WebSocket frames are sent to a WebSocket server at `ws://localhost:8080`.


#### WEBSOCKETS


The `index.js` and `package.json` files are part of the Node application that does some exemplary WebSocket communication.
This application starts a WebSocket server that listens for incoming WebSocket connections on port `8080`. Additionally,
it starts a WebSocket client that connects to Membrane on port `9999` and then sends a message to the
WebSocket server through Membrane. The server responds to this message and the application closes.

---
See:
- [webSocket](https://membrane-api.io/docs/current/webSocket.html) reference