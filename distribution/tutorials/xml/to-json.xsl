<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="text" encoding="UTF-8"/>

    <!-- Root element -->
    <xsl:template match="books">
        { "books": [
            <xsl:apply-templates/>
        ] }
    </xsl:template>

    <!-- List entry -->
    <xsl:template match="book">
        {
            <xsl:apply-templates select="*"/>
        }
        <!-- Add comma between entries -->
        <xsl:if test="position()!=last()">,</xsl:if>
    </xsl:template>

    <!-- Map property -->
    <xsl:template match="*[text() and not(*)]">
        "<xsl:value-of select="name()"/>": "<xsl:value-of select="normalize-space(.)"/>"
        <xsl:if test="position()!=last()">,</xsl:if>
    </xsl:template>


</xsl:stylesheet>