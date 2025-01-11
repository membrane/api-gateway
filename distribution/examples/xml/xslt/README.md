### XSLT INTERCEPTOR

With the XSLTInterceptor you can apply XSLT stylesheets to requests and responses.


#### RUNNING THE EXAMPLE

Execute the following steps:

1. Go to the `examples/xslt` directory.

2. Execute `service-proxy.bat` or `service-proxy.sh`

2. Open the URL https://api.predic8.de/restnames/name.groovy?name=Pia in your browser.

3. Compare it with the response of http://localhost:2000/restnames/name.groovy?name=Pia


### HOW IT IS DONE

First take a look at the proxies.xml file.

```xml
<router>
  <serviceProxy port="2000">
    <response>
      <transform xslt="./reformat.xsl" />
    </response>
    <target host="api.predic8.de" port="443">
        <ssl />
    </target>
  </serviceProxy>
</router>
```

You will see
that there is a `<serviceProxy>` that directs calls to the port `2000` to [api.predic8.de](https://api.predic8.de).
Additionally, the `XSLTInterceptor` is set for the rule.
The interceptor will be called while processing each request and response.

Now take a closer look at the transform element.

```xml
<transform xslt="./reformat.xsl" />
```

You can reference stylesheets that will be applied to the request and response with the `xslt` attribute.
If you leave the attribute blank or do not specify them at all, no transformation will be done.
With the element above, the interceptor will apply the specified XSLT stylesheet to the response and request. 
To limit the transformation only to the request or response, use request or response elements to wrap the interceptor.
In this example, we wrapped the interceptor with a response element
so that the transformation is only applied to the response. 

```xml
<response>
  <transform xslt="./reformat.xsl" />
</response>		
```

Take a look at the stylesheet:

```xml
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="/restnames">
		<nameinfo>
			<name>
				<value><xsl:value-of select="nameinfo/name"/></value>
				<gender>
					<xsl:choose>
						<xsl:when test="nameinfo/male = 'true'">male</xsl:when>
						<xsl:when test="nameinfo/female = 'true'">female</xsl:when>
					</xsl:choose>
				</gender>
			</name>
			<countries>
				<xsl:for-each select="nameinfo/countries/country">
					<country><xsl:value-of select="."/></country>
				</xsl:for-each>
			</countries>
		</nameinfo>
	</xsl:template>
</xsl:stylesheet>
```
We will use the stylesheet to transform the REST resource into another representation.
When we open https://api.predic8.de/restnames/name.groovy?name=Pia in our browser,
the REST resource will return the following XML document. 

```xml
<restnames>
  <nameinfo>
    <name>Pia</name>
    <countries>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Netherlands">Netherlands</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Austria">Austria</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Sweden">Sweden</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Belgium">Belgium</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Frisia">Frisia</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Norway">Norway</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Finland">Finland</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Denmark">Denmark</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Italy">Italy</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Swiss">Swiss</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Germany">Germany</country>
      <country href="https://api.predic8.de/restnames/namesincountry.groovy?country=Spain">Spain</country>
    </countries>
    <gender>female first name</gender>
    <male>false</male>
    <female>true</female>
  </nameinfo>
</restnames>
```

When we open http://localhost:2000/restnames/name.groovy?name=Piain a browser, the response is transformed by Membrane,
and the browser will show the following document:

```xml
<nameinfo>
  <name>
    <value>Pia</value>
    <gender>female</gender>
  </name>
  <countries>
    <country>Netherlands</country>
    <country>Austria</country>
    <country>Sweden</country>
    <country>Belgium</country>
    <country>Frisia</country>
    <country>Norway</country>
    <country>Finland</country>
    <country>Denmark</country>
    <country>Italy</country>
    <country>Swiss</country>
    <country>Germany</country>
    <country>Spain</country>
  </countries>
</nameinfo>
```