# Content Based Routing using XPath

Using the switch interceptor Membrane allows you to route XML messages based on XPath expressions.


## Running the Example

In this example we will route three XML messages based on their content to three different destinations. 

To test the router we will use the command line tool curl that can transfer data with URL syntax. You can download it form the following location:
http://curl.haxx.se/download.html

To run the example execute the following steps:

1. Go to the folder `examples/cbr`

2. Start Membrane:

`service-proxy.sh` or 
`service-proxy.bat`

3. Send an order XML document to the API:

```sh
curl -d @order.xml localhost:2000
```

4. Take a look at the output of the console. You should see the line:

`"Normal order received."`

5. Send an express order to the API:

```sh
curl -d @express.xml localhost:2000
Express order received.
```

6. Send an import document:

```sh
curl -d @import.xml localhost:2000
Order contains import items.
```
