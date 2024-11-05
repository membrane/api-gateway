<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:template match="/restnames">
		<nameinfo>
			<name>
				<value><xsl:value-of select="nameinfo/name"/></value>
				<gender>
					<xsl:choose>
						<xsl:when test="nameinfo/male = 'true'">male</xsl:when>
						<xsl:when test="nameinfo/female = 'true'">female</xsl:when>
					</xsl:choose>
				</gender>
			</name>
			<countries>
				<xsl:for-each select="nameinfo/countries/country">
					<country><xsl:value-of select="."/></country>
				</xsl:for-each>
			</countries>
		</nameinfo>
	</xsl:template>
</xsl:stylesheet>