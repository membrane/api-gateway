ADD SOAP HEADER INTERCEPTOR

You can create custom interceptors by extending the AbstractInterceptor class.



RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Goto the examples/soap11/add-soap-header directory

2. Run compile-and-copy.sh

3. Execute service-proxy.bat or service-proxy.sh

4. Run this command curl -d @soap-message-without-header.xml http://localhost:2050 -H "Content-Type: application/xml"

5. On console you will see that security element gets added to the request of body



HOW IT IS DONE

First using compile-and-copy.sh, we compile our interceptor and copy the generated classes in the ./target/classes/com directory into classes folder in Membrane directory.

So when we start the membrane, it is able to find and load our custom interceptor.

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name so we can use our interceptor on <serviceProxy>. You can see it in the line below.

<spring:bean id="headerAddInterceptor" class="com.predic8.myInterceptor.SoapHeaderAdderInterceptor" />

Again in the proxies.xml file inside serviceProxy tag you can see that we added our header add interceptor using the id value we defined above.

<serviceProxy name="echo" port="2050">
    <interceptor refid="headerAddInterceptor"/>

Soap header adder interceptor checks if the request is xml and existence of Header element. If both conditions are met it adds Security element including password and username elements into the header element.

Also you might notice groovy interceptor in between response tags. This is used for echoing the request.
