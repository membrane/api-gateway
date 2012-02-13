REGEX URL REWRITE INTERCEPTOR

With the RegExURLRewriteInterceptor you can rewrite URLs by regular expressions. 



RUNNING THE EXAMPLE

In this example we will rewrite a simple URL. Take a look at the WSDL located at

http://www.thomas-bayer.com/axis2/services/BLZService?wsdl

We want to hide that this service is hosted by axis2. To do this we have to replace the part axis2 from the context path. We can achieve this by using the RewriteInterceptor as follows:

1. Go to the examples/rewriter directory.

2. Execute router.bat

2. Open the URL http://localhost:2000/bank/services/BLZService?wsdl in your browser.



HOW IT IS DONE

This section describes the example in detail.  

First take a look at the rewriter.proxies.xml file.

<proxies>
	<serviceProxy port="2000">
		<rewriter>
			<map from="^/bank/(.*)" to="/axis2/$1" />
		</rewriter>
		<target host="www.thomas-bayer.com" port="80" />
	</serviceProxy>
</proxies>



You will see that there is a serviceProxy that directs calls to the port 2000 to www.thomas-bayer.com:80. Additionally the RewriteInterceptor is added the proxy. The interceptor will be called during the processing of each request and response.

Now take a closer look at the <rewriter> element:

<rewriter>
		<map from="^/bank/(.*)" to="/axis2/$1" />
</rewriter>

The interceptor is configured with one regular expression ^/bank/(.*) . This will match the following path-query parts of URIs:

/bank/services/BLZService?wsdl
/bank/services/BLZService

In the regular expression, we use a group that we can reference when replacing the path-query part of an URI. The value to replace the part is given by the "to" attribute and is set to /axis/$1 . The $1 is the reference to the group and will contain the value matched by the group. So the above parts will be replaced by the following:

/axis2/services/BLZService?wsdl
/axis2/services/BLZService
 
