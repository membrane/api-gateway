<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
							  xmlns:s11="http://schemas.xmlsoap.org/soap/envelope/">
	<xsl:template match="/">
		<s11:Envelope >
		  <s11:Body>
		    <blz:getBank xmlns:blz="http://thomas-bayer.com/blz/">
		      <blz:blz><xsl:value-of select="//path/component[2]"/></blz:blz>
		    </blz:getBank>
		  </s11:Body>
		</s11:Envelope>	
	</xsl:template>
</xsl:stylesheet>