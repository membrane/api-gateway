<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
				xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:old="https://predic8.de/old">

	<xsl:output omit-xml-declaration="yes" />
	
	<!-- Change namespace of getCity -->
	<xsl:template match="old:getCity">
		<xsl:element name="getCity" namespace="https://predic8.de/cities">
			<xsl:apply-templates select="*|@*"/>
		</xsl:element>
	</xsl:template>

	<!-- Rename element city-name to city -->
	<xsl:template match="city-name">
		<name><xsl:value-of select="."/></name>
	</xsl:template>

	<!-- Copy all other elements -->
	<xsl:template match="*|@*|text()">
		<xsl:copy><xsl:apply-templates select="*|@*|text()"/></xsl:copy>
	</xsl:template>

</xsl:stylesheet> 