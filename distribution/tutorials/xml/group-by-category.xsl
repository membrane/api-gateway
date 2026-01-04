<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="xml" indent="yes"/>
  <xsl:strip-space elements="*"/>

  <xsl:key name="books-by-category"
           match="book"
           use="normalize-space(category)"/>

  <xsl:template match="/books">
    <categories>
      <xsl:for-each select="book[generate-id() = generate-id(key('books-by-category', normalize-space(category))[1])]">
        <xsl:sort select="normalize-space(category)"/>

        <category name="{normalize-space(category)}">
          <xsl:for-each select="key('books-by-category', normalize-space(category))">
            <xsl:sort select="number(@id)"/>
            <xsl:copy-of select="title"/>
          </xsl:for-each>
        </category>

      </xsl:for-each>
    </categories>
  </xsl:template>

</xsl:stylesheet>