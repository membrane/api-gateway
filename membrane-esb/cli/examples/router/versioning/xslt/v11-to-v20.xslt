<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

	<xsl:output omit-xml-declaration="yes" />
	
	<!-- map all old elements to new ones, add add 'email' as child to 'addContact' -->
	<xsl:template match="*[namespace-uri() = 'http://predic8.com/contactService/v11']">
		<xsl:element name="{name()}" namespace="http://predic8.com/contactService/v20">
			<xsl:apply-templates select="node()|@*"/>
			<xsl:if test="local-name() = 'addContact'">
				<xsl:element name="email" />
			</xsl:if>
		</xsl:element>
	</xsl:template>

	<!-- map all old attributes to new ones -->
	<xsl:template match="@*[namespace-uri() = 'http://predic8.com/contactService/v11']">
		<xsl:attribute name="{name()}" namespace="http://predic8.com/contactService/v20">
			<xsl:value-of select="." />
		</xsl:attribute>
	</xsl:template>

	<!-- leave other elements alone -->
	<xsl:template match="*|@*|text()">
		<xsl:copy><xsl:apply-templates select="*|@*|text()"/></xsl:copy>
	</xsl:template>

</xsl:stylesheet> 