<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:strip-space elements="countries"/>
	
	<xsl:template match="nameinfo">
		<xsl:apply-templates />
	</xsl:template>

	<xsl:template match="country"><xsl:value-of select="."/><xsl:text>, </xsl:text></xsl:template>

	<xsl:template match="male|female"/>
	
	<xsl:template match="@*|node()|/">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates select="node()|text()"/>
		</xsl:copy>
	</xsl:template>	
</xsl:stylesheet>