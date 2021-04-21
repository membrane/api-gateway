CUSTOM INTERCEPTOR

You can create custom interceptors by extending the AbstractInterceptor class.
     


RUNNING THE EXAMPLE

In this example we will install a custom interceptor called MyInterceptor that prints a message to the console when it is invoked. 

To run the example execute the following steps:

1. Goto the examples/custom-interceptor-maven/ directory

2. Run the compile-and-copy.sh. Wait until the console window closes

3. Execute service-proxy.bat or service-proxy.sh

4. Open http://localhost:2000/

5. Take a look at the console.


HOW IT IS DONE

First using compile-and-copy.sh, we compile our interceptor and copy the generated classes in the ./target/classes/com directory into classes folder
in Membrane directory.

So when we start the membrane, it is able to find and load our custom interceptor.

In the proxies.xml file, we define name for our interceptor and write its fully qualified name so we can use our interceptor on <serviceProxy>. You can see in the line below.

<spring:bean id="myInterceptor" class="com.predic8.myInterceptor.MyInterceptor" />


In below part of proxies.xml you can see that we put our own interceptor in between <serviceProxy> so that our interceptor will run on requests and responses.

<serviceProxy port="2000">
	<interceptor refid="myInterceptor" />
	<target host="membrane-soa.org" />
</serviceProxy>

When we run the membrane using service-proxy.sh you can see that in the console that requests and responses are being intercepted by our custom interceptor.








 change this stuff

This section describes the example in detail.  

First take a look at the proxies.xml file.

	<router>
		<serviceProxy port="2000">
			<rateLimiter requestLimit="3" requestLimitDuration="PT30S"/>
			<target host="www.google.de" port="80" />
		</serviceProxy>
	</router>

You will see that there is a serviceProxy on port 2000. Additionally the RateLimiter is added to the proxy and configured to 3 requests per 30 seconds.

The rateLimiter element has 2 values that you can set and by default it is set to 1000 requests per hour.

requestLimit="x" can be any positive number. Specifies the number of requests per interval.
requestLimitDuration="PTxS" can be any duration in seconds. Specifies the interval in which requestLimit requests 
