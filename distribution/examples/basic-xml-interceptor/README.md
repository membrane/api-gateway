###BASIC XML INTERCEPTOR

In this example we will install an interceptor called BasicXmlInterceptor which adds date element with current time information inside bar element.
     


####RUNNING THE EXAMPLE


To run the example execute the following steps:

1. Goto the examples/basic-xml-interceptor directory
2. Run below command

  ``` 
   mvn package
  ```
3. Execute service-proxy.bat or service-proxy.sh
4. Open a second terminal
5. Run below command

```
curl -d @example.xml http://localhost:2000 -H "Content-Type: application/xml"
```
6. You can see on the console, the date tag was added with current date information


###HOW IT IS DONE

Using maven, we create a jar file and copy the compiled jar file into the libs directory of membrane to make the new interceptor available to the router.

So when we start the membrane, it is able to find and load our custom interceptor.

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name, so we can use our interceptor on serviceProxy. You can see it in the line below.
```
<spring:bean id="basicXmlInterceptor" class="com.predic8.myInterceptor.BasicXmlInterceptor" />
```
Again in the proxies.xml file inside `<serviceProxy> tag you can see that we added our basic xml interceptor using the beanname we defined above.
```
<serviceProxy name="echo" port="2000">
	<interceptor refid="basicXmlInterceptor"/>
```
Our interceptor checks if the content of the request is xml and creates a date element which contains current time information and adds it into the bar element using Java DOM API. 
