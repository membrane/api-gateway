### CUSTOM WEBSOCKET INTERCEPTOR WITH EMBEDDED MEMBRANE

In this example an exemplary application is created that starts an embedded Membrane instance with an added
custom WebSocket interceptor.


#### PREREQUISITES

1. Download and install Maven from https://maven.apache.org/download.cgi. Maven is used for (easier) compilation.
   
2. Prepare a WebSocket client and server. The server should listen on port `8080`. The client should connect through port 9999.
   (Membrane already ships with an example WebSocket client and server (requiring NodeJS). Take a look at
   the `websocket-intercepting` example and run the `startWebsocket.[bat|sh]`. This will start up a server and client and
   do some communication.)

   
#### RUNNING THE EXAMPLE

1. Start the embedded Membrane by running `start.[sh|bat]`. This can take some minutes the first time. Wait
   until you read `Starting finished - Waiting for WebSocket communication`
   
2. Start the WebSocket Server
   
3. Start the WebSocket client
   
4. Observe your custom interceptor in action (the example custom interceptor should log to the console)


#### HOW IT IS DONE

This sample project is a Maven project. To learn more about Maven please visit https://maven.apache.org/index.html.
Membrane was added to the project with Maven. Maven also helps with compilation.

The custom interceptor is found in the `src/main/java/com/predic8/membrane/core/interceptor/websocket/custom` folder. It
implements `WebSocketInterceptorInterface` and logs every frame to the console.

The application starter is found in the `src/main/java/com/predic8/application` folder. The application starter starts an
embedded Membrane and adds a service proxy on port `9999` to it that has the WebSocket log interceptor attached.

---
See:
- [webSocket](https://membrane-api.io/docs/current/webSocket.html) reference
- [wsInterceptor](https://membrane-api.io/docs/current/wsInterceptor.html) reference