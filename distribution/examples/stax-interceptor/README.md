###CUSTOM STAX INTERCEPTOR

In this example we will install an interceptor called StaxConverterInterceptor that changes tag name from `<foo>` to `<bar>` using Java STAX API.


     
####RUNNING THE EXAMPLE

To run the example execute the following steps:

1. Go to the examples/stax-interceptor/ directory

2. Run below command

  ``` 
   mvn package
  ```
3. Execute `service-proxy.bat` or `service-proxy.sh`
4. Open a second terminal
5. Run below command

```
curl -d @example.xml http://localhost:2000 -H "Content-Type: application/xml"
```

6. In the console you can see tht`<foo>` got changed to `<bar>`



####HOW IT IS DONE

Using maven, we create a jar file and copy the compiled jar file into the libs directory of membrane to make the new interceptor available to the router.

In the proxies.xml file, we define a name for our interceptor and write its fully qualified name, so we can use our interceptor on `<serviceProxy>`. You can see it in the line below.
```
<spring:bean id="staxInterceptor" class="com.predic8.myInterceptor.StaxConverterInterceptor" />
```

In below, we have a part of proxies.xml file. You can see that we put our own interceptor in between `<request>` so that our interceptor will run on only requests.
```
<request>
    <interceptor refid="staxInterceptor"/>
</request>
```
When we make the above request using curl, our interceptor changes the request body using Java STAX API and renames the foo tag to bar.

Also, you might notice groovy interceptor in between response tags. It is used for demonstration.
