<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes"/>

    <!-- Set from the "library" exchange property -->
    <xsl:param name="library"/>

    <xsl:template match="/books">
        <books library="{$library}">
            <xsl:copy-of select="book"/>
        </books>
    </xsl:template>

</xsl:stylesheet>
