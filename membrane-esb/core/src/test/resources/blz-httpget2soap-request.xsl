<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
							  xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"
							  xmlns:ns1="http://thomas-bayer.com/blz/">
	<xsl:template match="/">
		<s11:Envelope >
		  <s11:Body>
		    <ns1:getBank>
		      <ns1:blz><xsl:value-of select="//path/component[2]"/></ns1:blz>
		    </ns1:getBank>
		  </s11:Body>
		</s11:Envelope>	
	</xsl:template>
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates/>
		</xsl:copy>
	</xsl:template>	
</xsl:stylesheet>