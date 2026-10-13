### CUSTOM WEBSOCKET INTERCEPTORS


In this folder you will find two sub-folders. Both show different ways how you can add your own WebSocket interceptor to Membrane.

The embedded example creates an embedded Membrane instance and adds the custom WebSocket interceptor.

The standalone example compiles the custom WebSocket interceptor and integrates it into a standalone Membrane application.

A custom WebSocket interceptor implements `com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface`. See the
[wsInterceptor](https://membrane-api.io/docs/current/wsInterceptor.html) reference.

