### XSLT INTERCEPTOR

With the XSLTInterceptor you can apply XSLT stylesheets to requests and responses.


#### RUNNING THE EXAMPLE

At the following URL you will get a REST representation of a customer resource.


http://www.thomas-bayer.com/samples/sqlrest/CUSTOMER/7/ 


In this example we will transform that representation with the XSLTInterceptor. 


Execute the following steps:

1. Go to the `examples/xslt` directory.

2. Execute `service-proxy.bat` or `service-proxy.sh`

2. Open the URL http://www.thomas-bayer.com/samples/sqlrest/CUSTOMER/7/ in your browser.

3. Compare it with the response of http://localhost:2000/samples/sqlrest/CUSTOMER/7/


### HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the proxies.xml file.


```
<proxies>
	<serviceProxy port="2000">
		<response>
			<transform xslt="customer2person.xsl" />
		</response>		
		<target host="www.thomas-bayer.com" port="80" />
	</serviceProxy>
</proxies>
```

You will see that there is a `<serviceProxy>` that directs calls to the port `2000` to www.thomas-bayer.com. Additionally, the `XSLTInterceptor` is set for the rule. The interceptor will be called while processing each request and response.

Now take a closer look at the transform element.

```
<transform xslt="examples/xslt/customer2person.xsl" />
```

You can reference stylesheets that will be applied to the request and response with the `xslt` attribute. If you leave the attribute blank or do not specify them at all, no transformation will be done. With the element above the interceptor will apply the specified XSLT stylesheet to the response and request. To limit the transformation only to the request or response, use request or response elements to wrap the interceptor. In this example we wrapped the interceptor with an response element so that the transformation is only applied to the response. 

```
<response>
	<transform xslt="customer2person.xsl" />
</response>		
```

Take a look at the stylesheet:

```
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="/CUSTOMER">
		<person>
			<name>
				<first><xsl:value-of select="FIRSTNAME" /></first>
				<last><xsl:value-of select="LASTNAME" /></last>
			</name>
			<address>
				<street><xsl:value-of select="STREET" /></street>
				<city><xsl:value-of select="CITY" /></city>
			</address>
		</person>
	</xsl:template>
	
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates select="node()|text()"/>
		</xsl:copy>
	</xsl:template>	
</xsl:stylesheet>
```
We will use the stylesheet to transform the REST resource into another representation. When we open http://www.thomas-bayer.com/samples/sqlrest/CUSTOMER/7/ in our browser, the REST resource will return the following XML document. 

```
<?xml version="1.0"?><CUSTOMER xmlns:xlink="http://www.w3.org/1999/xlink">
    <ID>7</ID>
    <FIRSTNAME>Roger</FIRSTNAME>
    <LASTNAME>Seid</LASTNAME>
    <STREET>3738 N Monroe St</STREET>
    <CITY>Tallahassee</CITY>
</CUSTOMER>
```

When we open http://localhost:2000/samples/sqlrest/CUSTOMER/7/ in a browser the response is transformed by Membrane and the browser will show the following document:

```
<?xml version="1.0" encoding="UTF-8"?>
<person>
	<name>
		<first>Roger</first>
		<last>Seid</last>
	</name>
	<address>
		<street>3738 N Monroe St</street>
		<city>Tallahassee</city>
	</address>
</person>
```