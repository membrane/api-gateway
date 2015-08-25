SWAGGER REWRITER EXAMPLE

In this example we will run a Proxy for Swagger using Membrane.
Swagger is a specification for standardized REST APIs (http://swagger.io/).
Specification for Version 2.0 is available at
  https://github.com/swagger-api/swagger-spec/blob/master/versions/2.0.md



PREPARATIONS

1.	Go to the examples/swagger directory.
2.	Execute service-proxy.sh / service-proxy.bat



RUNNING THE EXAMPLE

Let's proxy some Swagger!

To run the example execute the following steps:

1.	Open the URL http://localhost:8000/ in your browser
2.	Open another tab in your browser with the URL http://petstore.swagger.io/
3.	The websites on localhost:8000 and petstore.swagger.io should look the same,
	except that all URLs on the former are rewritten to localhost:8000.
	You can also navigate on both sites: click on "pet" and explore the REST API.
4.	Run the following calls in the command line, they should yield the same result.
	curl -X GET --header "Accept: application/json" "http://localhost:8000/v2/pet/findByStatus?status=pending"
	curl -X GET --header "Accept: application/json" "http://petstore.swagger.io/v2/pet/findByStatus?status=pending"



HOW IT IS DONE

The following part describes the example in detail.

First, take a look at the proxies.xml file.

	<serviceProxy port="8000" >
		<swaggerRewriter />
		<if test="exc.request.uri.endsWith('swagger-ui.js') || exc.request.uri == '/'">
			<regExReplacer
				regex="petstore.swagger.io"
				replace="localhost:8000"
				onlyTextContent="false" />
		</if>
		<target host="petstore.swagger.io" port="80" />
	</serviceProxy>

The swaggerRewriter does the rewriting of the host URL in the Swagger Object
that is initially transferred.
The regExReplacer does some rewriting in a script that is used by the petstore
example site. Without it, the URL in the text field at the top wouldn't be
rewritten.
Lastly, the target specifies the site which the swaggerRewriter should actually
rewrite.
