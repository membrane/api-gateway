XSLT INTERCEPTOR

With the XSLTInterceptor you can apply XSLT stylesheets to requests and responses.



RUNNING THE EXAMPLE

At the following URL you will get a REST representation of a customer resource.

http://www.thomas-bayer.com/sqlrest/CUSTOMER/-20/ 

In this example we will transform that representation with the XSLTInterceptor. 

Execute the following steps:

1. Start Membrane Monitor.
2. Click on "File/Load Configuration" and open the file examples/xslt/rules.xml.
3. Open the URL http://www.thomas-bayer.com/sqlrest/CUSTOMER/-20/ in your browser.
4. Compare it with the response of http://localhost:2000/sqlrest/CUSTOMER/-20/



HOW IT IS DONE

The following part describes the example in detail.  

First take a look at the rules.xml file.


<configuration>
  <rules>
    <forwarding-rule name="SQLREST Customer"
                      port="2000"
                      host="*"
                      method="*">
      <targetport>80</targetport>
      <targethost>www.thomas-bayer.com</targethost>
      <interceptors>
        <interceptor id="xsltTransformer"
                      name="XSLT Transformer" />
      </interceptors>
    </forwarding-rule>
  </rules>
</configuration>



You will see that there is a rule that directs calls to the port 2000 to www.thomas-bayer.com:80. Additionally the XSLTInterceptor is set for the rule. The interceptor will be called during the processing of each request and response.

Now take a look at the bean configuration of the interceptor in the configuration/monitor-beans.xml file.


<bean id="xsltTransformer" class="com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor">
	<property name="displayName" value="XSLT Transformer" />
	<property name="requestXSLT" value="" /> 
	<property name="responseXSLT" value="examples/xslt/customer2person.xsl" /> 
</bean> 


The interceptor is only configured to apply an XSLT stylesheet to the response. 

Take a look at the stylesheet:


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

We will use the stylesheet to transform the REST resource into another representation. When we open http://www.thomas-bayer.com/sqlrest/CUSTOMER/-20/ in our browser, the REST resource will return the following XML document. 


<CUSTOMER>
    <ID>-20</ID>
    <FIRSTNAME>Rick</FIRSTNAME>
    <LASTNAME>Cortés Ribotta</LASTNAME>
    <STREET>Calle Pública "B" 5240 Casa 121</STREET>
    <CITY>Omaha</CITY>
</CUSTOMER>


When we open http://localhost:2000/sqlrest/CUSTOMER/-20/ in a browser the response is transformed by Membrane and the browser will show the following document:


<person>
  <name>
    <first>Rick</first>
    <last>Cortés Ribotta</last>
  </name>
  <address>
    <street>Calle Pública "B" 5240 Casa 121</street>
    <city>Omaha</city>
  </address>
</person>
