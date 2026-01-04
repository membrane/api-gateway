<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/">
    <stats>
      <elements>
        <xsl:value-of select="count(//*)"/>
      </elements>
      <attributes>
        <xsl:value-of select="count(//@*)"/>
      </attributes>
    </stats>
  </xsl:template>

</xsl:stylesheet>
