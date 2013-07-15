CUSTOM INTERCEPTOR

You can create custom interceptors by extending the AbstractInterceptor class.
     
RUNNING THE EXAMPLE

In this example we will install a custom interceptor called MyInterceptor that prints a message to the console when it is invoked. 

To run the example execute the following steps:

1. Goto the examples/custom-interceptor/ directory

2. Compile the Java sources by executing "ant compile" in the examples/custom-interceptor/ folder.

3. Copy the directory build/classes/com/ to [MEMBRANE_HOME]/classes

4. Execute service-proxy.bat or service-proxy.sh

5. Open http://localhost:2000/

6. Take a look at the console.
