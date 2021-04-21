BASIC XML INTERCEPTOR

In this example we will install a interceptor called BasicXmlInterceptor which doubles the value on example tag.
     


RUNNING THE EXAMPLE


To run the example execute the following steps:

1. Goto the examples/soap11/basic-xml-interceptor directory

2. Run compile-and-copy.sh

3. Execute service-proxy.bat or service-proxy.sh

4. Run this command curl -d @example.xml http://localhost:2050 -H "Content-Type: application/xml"

5. You can see on console, the date tag was added with current date information


HOW IT IS DONE

First using compile-and-copy.sh, we compile our interceptor and copy the generated classes in the ./target/classes/com directory into classes folder in Membrane directory.

So when we start the membrane, it is able to find and load our custom interceptor.

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name so we can use our interceptor on <serviceProxy>. You can see it in the line below.

<spring:bean id="basicXmlInterceptor" class="com.predic8.myInterceptor.BasicXmlInterceptor" />

Again in the proxies.xml file inside serviceProxy tag you can see that we added our basic xml interceptor using the id value we defined above.

<serviceProxy name="echo" port="2050">
	<interceptor refid="basicXmlInterceptor"/>

Our interceptor takes example.xml from body of the request, creates date element which contains current time information and adds it into the bar element using java dom api. 
