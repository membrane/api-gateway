SHUTDOWN INTERCEPTOR

Using the ShutdownInterceptor, Membrane Monitor and Service Proxy can shutdown on HTTP request.

RUNNING THE EXAMPLE

In this example we will stop Membrane Service proxy via HTTP request.

To run the example execute the following steps:

1. Go to the examples/shutdown directory.

2. Execute service-proxy.bat

3. Send POST to the URL http://localhost:2000/shutdown with "some text including 'secret code' in the content" 

4. Take a look at the output of the console.

5. service-proxy.bat should end


Troubleshooting:

If Membrane does not generate any ouput after loading an URL, it is possible that your browser has already cached the ressource. 

Shortcuts for the different browsers:

Safari: ALT + click reload or CMD + SHIFT + R while using a mac
Firefox: SHIFT + click reload
Chrome: SHIFT + F5 or CONTROL + F5
IE: CONTROL + F5
