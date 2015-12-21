<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
							  xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
							  
	<xsl:template match="/">
		<xsl:apply-templates select="//s11:Body/*"/>
	</xsl:template>
	
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates />
		</xsl:copy>
	</xsl:template>	
	
	<!-- Get rid of the namespace prefixes in json. So
	  
	     ns1:getBank will be just getBank   
	-->
	<xsl:template match="*">
		<xsl:element name="{local-name()}">
			<xsl:apply-templates/>
		</xsl:element>
	</xsl:template>
	
</xsl:stylesheet>