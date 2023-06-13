###  ADD SOAP HEADER INTERCEPTOR

This interceptor adds a SOAP header to incoming request using the Java DOM API.


####  RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Goto the examples/soap11/add-soap-header directory
2. Run below command

  ``` 
   mvn package
  ```
3. Execute `service-proxy.bat` or `service-proxy.sh`
4. Open a second terminal
5. Run below command

```
curl -d @soap-message-without-header.xml http://localhost:2000 -H "Content-Type: application/xml"
```
6. On the console you will see that a security XML-element gets added to the request of the body


####  HOW IT IS DONE

Using maven, we create a jar file and copy the compiled jar file into the libs directory of membrane to make the new interceptor available to the router. 

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name, so we can use our interceptor on `<serviceProxy>. You can see it in the line below.

``` 
<spring:bean id="headerAddInterceptor" class="com.predic8.AddSoapHeaderInterceptor" />
``` 
Again in the proxies.xml file inside `<serviceProxy>` tag you can see that we added the interceptor using the beanname we defined above.

``` 
<serviceProxy name="echo" port="2000">
  <interceptor refid="headerAddInterceptor"/>
``` 
The add header interceptor checks if the content of the request is xml and the existence of a Header element. If both conditions are met it adds a Security element to the SOAP header. Have a look at the AddSoapHeaderInterceptor source code as well.

Also, you might notice the groovy interceptor. This is just used for sending a request back. Usually there is a target-Element instead. 
