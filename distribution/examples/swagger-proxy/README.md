### SWAGGER REWRITER EXAMPLE

In this example we will run a proxy for Swagger using Membrane.
Swagger is a specification for standardized REST APIs (http://swagger.io/).
Specification for Version 2.0 is available at
  https://github.com/swagger-api/swagger-spec/blob/master/versions/2.0.md


#### PREPARATIONS

1.	Go to the `examples/swagger` directory.
2.	Execute `service-proxy.sh` or `service-proxy.bat`


#### RUNNING THE EXAMPLE

Let's proxy some Swagger!

To run the example execute the following steps:

1.	Open the URL http://localhost:7000/ in your browser
2.	Open another tab in your browser with the URL http://petstore.swagger.io/
	The websites on `localhost:7000` and `petstore.swagger.io` should look the same,
	except that all URLs on the former are rewritten to `localhost:7000`.
	You can also navigate on both sites: click on "pet" and explore the REST API.
3.	Run the following calls in the command line, they should yield the same result.
```
	curl -X GET --header "Accept: application/json" "http://localhost:7000/v2/pet/8"
	curl -X GET --header "Accept: application/json" "http://petstore.swagger.io/v2/pet/8"
```

4.	Try a call that isn't a part of the Swagger specification (we added an 's' to 'pet'),
	you should get a Bad Request error message from Membrane, since it doesn't accept the call:
	`curl -X GET --header "Accept: application/json" "http://localhost:7000/v2/pets/8"`


#### HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the proxies.xml file.
```
<swaggerProxy port="7000" url="http://petstore.swagger.io/v2/swagger.json" />

<serviceProxy port="8000">
	<swaggerRewriter />
	<target host="petstore.swagger.io" port="80" />
</serviceProxy>
```
There are three Service Proxies defined here:

The first and the second are both Service Proxies with function as a Swagger
Proxy.

The first one, the swaggerProxy
```
<swaggerProxy port="7000" url="http://petstore.swagger.io/v2/swagger.json" />
```
offers a shorter and more concise way to define a Swagger Proxy,
whereas the second method
```
<serviceProxy port="8000">
	<swaggerRewriter />
	<target host="petstore.swagger.io" port="80" />
</serviceProxy>
```
is more explicit and has more potential for more advanced setups.

However, even though these two methods both provide a Swagger Rewriting Proxy,
they are not identical.
The `<swaggerProxy>` only lets through
1) the swagger.json
2) Swagger calls which are part of the Swagger specification (swagger.json)
3) and calls to the Swagger UI. (if swaggerProxy's 'allowUI' attribute is true (default=true))

The second method - with a `<swaggerRewriter>` inside a `<serviceProxy>` - let's through
all calls and redirects them to `petstore.swagger.io` on port `80.`
The `<swaggerRewriter>` takes care of rewriting the `swagger.json` specification, and
the Swagger UI (if `<swaggerRewriter>`'s attribute `rewriteUI` is set (default=true)).
When this option is set, it also rewrites the URL of html and javascript files
that are transferred.
Lastly, the target specifies the site which the swaggerRewriter should actually
rewrite.

The third Service Proxy is just an Admin Console which you can use to watch
active Service Proxies and current calls and connections.
