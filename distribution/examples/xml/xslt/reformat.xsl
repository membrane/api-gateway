<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="/restnames">
			<info name="{nameinfo/name}">
				<xsl:attribute name="gender">
					<xsl:choose>
						<xsl:when test="nameinfo/male = 'true'">male</xsl:when>
						<xsl:when test="nameinfo/female = 'true'">female</xsl:when>
					</xsl:choose>
				</xsl:attribute>
				<countries>
					<xsl:apply-templates select="//country"/>
				</countries>
			</info>
	</xsl:template>

	<xsl:template match="country[last()]">
		<xsl:value-of select="."/>
	</xsl:template>

	<xsl:template match="country">
		<xsl:value-of select="."/><xsl:text>, </xsl:text>
	</xsl:template>

	<xsl:template match="*"/>

</xsl:stylesheet>