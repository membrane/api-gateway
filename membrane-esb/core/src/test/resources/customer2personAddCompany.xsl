<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:param name="XSLT_COMPANY" />
	
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
			<company><xsl:value-of select="$XSLT_COMPANY"/></company>
		</person>
	</xsl:template>
	
	<xsl:template match="@*|node()">
		<xsl:copy>
			<xsl:apply-templates select="@*"/>
			<xsl:apply-templates select="node()|text()"/>
		</xsl:copy>
	</xsl:template>	
</xsl:stylesheet>