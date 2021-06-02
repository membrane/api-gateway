### CUSTOM INTERCEPTOR

You can create custom interceptors by extending the `AbstractInterceptor` class.


#### RUNNING THE EXAMPLE

In this example we will install a custom interceptor called `MyInterceptor` that prints a message to the console when it is invoked. 

To run the example execute the following steps:

1. Goto the `examples/custom-interceptor/` directory

2. Run below command

  ``` 
   mvn package
  ```

3. Execute service-proxy.bat or service-proxy.sh

4. Open http://localhost:2000/ using a browser

5. Take a look at the console.


#### HOW IT IS DONE

Using maven, we create a jar file and copy the compiled jar file into the libs directory of membrane to make the new interceptor available to the router.

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name, so we can use our interceptor on <serviceProxy>. You can see it in the line below.

``` 
<spring:bean id="myInterceptor" class="com.predic8.MyInterceptor" />
``` 


Again in the proxies.xml file inside `<serviceProxy>` tag you can see that we added the interceptor using the beanname we defined above.

```
<serviceProxy port="2000">
	<interceptor refid="myInterceptor" />
	<target host="membrane-soa.org" />
</serviceProxy>
```

When we run the membrane using service-proxy.sh you can see that in the console that requests and responses are being intercepted by our custom interceptor.

