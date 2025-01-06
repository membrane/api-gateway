<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
				xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:new="https://predic8.de/cities">

	<xsl:output omit-xml-declaration="yes" />
	
	<!-- Change namespace of getCity -->
	<xsl:template match="new:getCityResponse">
		<xsl:element name="getCityResponse" namespace="https://predic8.de/old">
			<xsl:apply-templates select="*|@*"/>
		</xsl:element>
	</xsl:template>

	<!-- Copy all other elements -->
	<xsl:template match="*|@*|text()">
		<xsl:copy><xsl:apply-templates select="*|@*|text()"/></xsl:copy>
	</xsl:template>

</xsl:stylesheet> 