### CUSTOM WEBSOCKET INTERCEPTOR WITH EMBEDDED MEMBRANE

In this example a custom WebSocket interceptor is created and then added to Membrane Service Proxy through configuration
of the proxies.xml file.


#### PREREQUISITES

1. Prepare a WebSocket client and server. The server should listen on port 8080. The client should connect through port 9999.
   (Membrane Service Proxy already ships with an example WebSocket client and server (requiring NodeJS). Take a look at
   the "websocket-intercepting" example and run the startWebsocket.[bat|sh]. This will start up a server and client and
   do some communication.)

   
#### RUNNING THE EXAMPLE

1. Run the `compile-and-copy.[bat|sh]`. Wait until the console window closes
2. Start Membrane Service proxy by running the `service-proxy.[bat|sh]` in this folder
3. Look at the console window and wait until `"Membrane ... up and running!"`. This window will remain open
4. Start the WebSocket Server
5. Start the WebSocket client
6. Observe your custom interceptor in action (the example custom interceptor should log to the console)


#### HOW IT IS DONE

The custom interceptor is found in the  `src/main/java/com/predic8/membrane/core/interceptor/websocket/custom` folder. This
is the WebSocket log interceptor of the custom WebSocket interceptor tutorial at `<LINK TO CUSTOM WEBSOCKET INTERCEPTOR>`.

The custom interceptor is compiled and added to Membrane Service Proxy by copying the resulting jar into the `lib` folder.

Take a look at the proxies.xml. To use the interceptor in Membrane we need to create it as a spring bean. This is done like this:
```
<spring:bean id="myInterceptor" class="com.predic8.membrane.core.interceptor.websocket.custom.MyWebSocketLogInterceptor" />
```

The id is the name you want to use to reference your bean in the configuration file. The class attribute tells spring
which class it should instantiate.

You can then reference this interceptor in through the wsInterceptor by giving it the id of you interceptor:

```
<wsInterceptor refid="myInterceptor"/>
```