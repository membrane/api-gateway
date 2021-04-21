CUSTOM STAX INTERCEPTOR

In this example we will install a interceptor called StaxConverterInterceptor that changes tag name from foo to bar using java stax api.


     
RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Go to the examples/stax-interceptor/ directory

2. Run compile-and-copy.sh

4. Execute service-proxy.bat or service-proxy.sh

5. Run this command curl -d @example.xml http://localhost:2050 -H "Content-Type: application/xml"

6. You can see in console name of foo got changed to bar



HOW IT IS DONE

First using compile-and-copy.sh, we compile our interceptor and copy the generated classes in the ./target/classes/com directory into classes folder
in Membrane directory.

So when we start the membrane, it is able to find and load our custom interceptor.

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name so we can use our interceptor on <serviceProxy>. You can see it in the line below.

<spring:bean id="staxInterceptor" class="com.predic8.myInterceptor.StaxConverterInterceptor" />

In below, we have a part of proxies.xml file. You can see that we put our own interceptor in between <request> so that our interceptor will run on only requests.

<request>
    <interceptor refid="staxInterceptor"/>
</request>

When we make the above request using curl, our interceptor changes the request body using java stax api and renames the foo tag to bar.

Also you might notice groovy interceptor in between response tags. This is used for echoing the request.
