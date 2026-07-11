<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="xml" indent="yes"/>

    <!-- Each param is set from the exchange property of the same name -->
    <xsl:param name="library"/>
    <xsl:param name="generated"/>
    <xsl:param name="requestId"/>

    <xsl:template match="/books">
        <books library="{$library}" generated="{$generated}" requestId="{$requestId}">
            <xsl:copy-of select="book"/>
        </books>
    </xsl:template>

</xsl:stylesheet>
