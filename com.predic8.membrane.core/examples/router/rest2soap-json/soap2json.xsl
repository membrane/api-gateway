<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
							  xmlns:blz="http://thomas-bayer.com/blz/"
							  xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
			
	<xsl:output method="text" />
					  
	<xsl:template match="/">
		<xsl:text>{&#10;</xsl:text>
			<xsl:for-each select="//blz:details/*" >
				<xsl:text>&#9;</xsl:text><xsl:value-of select="local-name()"/><xsl:text>:"</xsl:text><xsl:value-of select="." /><xsl:text>"</xsl:text>
				<xsl:if test="not(position()=last())">
					<xsl:text>,&#10;</xsl:text>
      			</xsl:if>
      		</xsl:for-each>
		<xsl:text>&#10;}</xsl:text>
	</xsl:template>
		
</xsl:stylesheet>