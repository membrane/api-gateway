### CUSTOM WEBSOCKET INTERCEPTOR WITH EMBEDDED MEMBRANE

In this example an exemplary application is created that starts an embedded Membrane Service Proxy instance with added
custom WebSocket interceptor.


#### PREREQUISITES

1. Download and install Maven from https://maven.apache.org/download.cgi. Maven is used for (easier) compilation.
   
2. Prepare a WebSocket client and server. The server should listen on port `8080`. The client should connect through port 9999.
   (Membrane Service Proxy already ships with an example WebSocket client and server (requiring NodeJS). Take a look at
   the `websocket-intercepting` example and run the `startWebsocket.[bat|sh]`. This will startup a server and client and
   do some communication.)-

   
#### RUNNING THE EXAMPLE

1. Start the embedded Membrane Service Proxy by starting `start.[bat|sh]`. This can take some minutes the first time. Wait
   until you read `Starting finished - Waiting for WebSocket communication`
   
2. Start the WebSocket Server
   
3. Start the WebSocket client
   
4. Observe your custom interceptor in action (the example custom interceptor should log to the console)


#### HOW IT IS DONE

This sample project is a Maven project. To learn more about Maven please visit https://maven.apache.org/index.html.
Membrane Service Proxy was added to the project with Maven. Maven also helps with compilation.

The custom interceptor is found in the `src/main/java/com/predic8/membrane/core/interceptor/websocket/custom` folder. This
is the WebSocket log interceptor of the custom WebSocket interceptor tutorial at `<LINK TO CUSTOM WEBSOCKET INTERCEPTOR>`.

The application starter is found in the `src/main/java/com/predic8/application` folder. The application starter starts an
embedded Membrane Service Proxy and adds a service proxy to it that has the WebSocket log interceptor attached.
