# Internal Proxy

This example explains how to realize complex message flows and how to break an api or serviceProxy into smaller units. Instead of an external target a request can be routed to an internal proxy. The internal proxy can offer reusable functionality that is shared by multiple APIs.

## SOAP Example

## Running the Example

Let's start first example with the `<soapProxy>`

1. Go to the `examples/internalProxy` directory

2. Execute `service-proxy.sh`

3. Go to [http://localhost:2000/axis2/services/BLZService](http://localhost:2000/axis2/services/BLZService)
 
4. Observe that `soapProxy` takes the `WSDL` file from `<internalProxy>`  and populates and serves the webpage
## How it is done

The following part describes the `<soapProxy>` example in detail.

Let's take a look at the `proxies_soap.xml` file.

In below line you can see definition of `<soapProxy>`

`<soapProxy wsdl="service:mysoapbackend/axis2/services/BLZService?wsdl" port="2000">`

When we put `URL`s starting with `service:` scheme in `wsdl` attribute, these URLs are resolved for  `<internalProxy>` with the name that comes after
`service:` part until next `/`.

In this example our `<soapProxy>` looks at value of `wsdl` attribute, parses `URL` and fetches the `WSDL` file from `<internalProxy>` defined in line below.

```<internalProxy name="mysoapbackend">```

As you can see our `<soapProxy>` knows which `<internalProxy>` it connects to, through the `name` attribute of `<internalProxy>`.

You can also run below command and create valid `SOAP` request that would go through `<soapProxy>` and `<internalProxy>` we defined
in `proxies_soap.xml` file. Don't forget to run it in the `examples/internalproxy` folder.
```
curl -d @soap_request.xml http://localhost:2000/axis2/services/BLZService --header "Content-Type: text/xml;charset=UTF-8" --header "SOAPAction:Get"
```

It is also possible to use interceptors in `<internalProxy>` like other proxies. This is useful when you have tasks that are common for other proxies.
You can put them in `<internalProxy>` and route other proxies using `service:` URL scheme.
If you ran above command you can see `X-Example-Header: true` header in the console you have run Membrane.
In our example we use `<groovy>` interceptor for putting header and printing it. Have a look at `examples/groovy` folder if you are interested.

### SERVICE PROXY EXAMPLE

#### RUNNING THE EXAMPLE

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
