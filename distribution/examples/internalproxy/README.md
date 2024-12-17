# Internal Proxy

This example explains how to realize complex message flows and how to break an api or serviceProxy into smaller units. Instead of an external target a request can be routed to an internal proxy. The internal proxy can offer reusable functionality that is shared by multiple APIs.

### RUNNING THE EXAMPLE

This section explains how to run the example with `<serviceProxy>`

1. Go to the `examples/internalProxy` directory

2. Go to the `service-proxy.sh` file and change  below line to second line below

``` 
java  -classpath "$CLASSPATH" com.predic8.membrane.core.Starter -c proxies_soap.xml
java  -classpath "$CLASSPATH" com.predic8.membrane.core.Starter -c proxies_service.xml
```
3. Execute `service-proxy.sh`

4. Execute `curl localhost:2000` in another console

5. Take a look at the output of the console that you are running Membrane. You should see the line: `"Inside proxy example_main."`

6. Now execute below command

```curl -d @express.xml localhost:2000```

7.Observe that additionally to above output we got `Inside proxy mybackend.`

#### HOW IT IS DONE

The following part describes the `<serviceProxy>` example in detail.

Open `proxies_service.xml` in text editor.

First we define a `<serviceProxy>` with like below.

```
    <serviceProxy port="2000" name="example_main">
```

When we run first command in step 4 from a command line, `<groovy>` interceptor we defined prints to the console

```
    <groovy>
      println("Inside proxy example_main.")
    </groovy>
```

You can see in file we have `<switch>` element. This is a `XPathCBRInterceptor`.

```
    <switch>
      <case xPath="//order[@express='yes']" service="mybackend" />
    </switch>
```

`XPathCBRInterceptor` changes the target of exchange based on XPath expressions.

In our example when we run the command from step 6, our `XPathCBRInterceptor` checks the `express.xml` file we put into request for given `XPath` expression and
sends the exchange to `mybackend` service if the condition is true.


`XPathCBRInterceptor` knows which proxy to send exchange to through `service` attribute.
Since our `internalProxy` has the name `mybackend`, Membrane sends the exchange to `<internalProxy>` defined like below.

```
<internalProxy name="mybackend">
```

---
See:
- [internalProxy](https://membrane-soa.org/api-gateway-doc/current/configuration/reference/internalProxy.htm) reference