<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
							  xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/"
							  exclude-result-prefixes="s11">
  
	<xsl:output omit-xml-declaration="yes"/>
    
	<xsl:template match="/">
		<xsl:apply-templates select="//s11:Body/*"/>
	</xsl:template>

	<xsl:template match="*">
		<!-- If you use copy unused namespaces of the source document will be copied as well-->
		<xsl:element name="{name()}"
                 namespace="{namespace-uri()}">
                 <xsl:apply-templates select="@* | node()"/>
        </xsl:element>
	</xsl:template>	
	
</xsl:stylesheet>