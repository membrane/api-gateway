### CUSTOM INTERCEPTOR

You can create custom interceptors by extending the `AbstractInterceptor` class.


#### RUNNING THE EXAMPLE

In this example we will install a custom interceptor called `MyInterceptor` that prints a message to the console when it is invoked. 

To run the example execute the following steps:

1. Goto the `examples/extending-membrane/custom-interceptor/` directory

2. Run below command

  ``` 
   mvn package
  ```

3. Execute membrane.cmd or membrane.sh

4. Open http://localhost:2000/ using a browser

5. Take a look at the console.


#### HOW IT IS DONE

Using maven, we create a jar file and copy the compiled jar file into the libs directory of membrane to make the new interceptor available to the router.

In the apis.yaml file, we define a name for our interceptor and write its fully qualified name, so we can use our interceptor in the flow:

```yaml
components:
  myInterceptor:
    bean:
      class: com.predic8.MyInterceptor
``` 


Again in the apis.yaml file inside `api` you can see that we added the interceptor using the beanname we defined above.

```yaml
api:
  port: 2000
  flow:
    - $ref: "#/components/myInterceptor"
    - return: {}
```

When we run the membrane using membrane.sh you can see that in the console that requests and responses are being intercepted by our custom interceptor.

---
See:
- [interceptor](https://membrane-api.io/docs/current/interceptor.html) reference
