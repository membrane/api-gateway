<?xml version="1.0" ?>
<!-- Conformance processor for the Schematron XML Schema Language.
	http://www.ascc.net/xml/resource/schematron/schematron.html
 
 Copyright (c) 2001 Rick Jelliffe and Academia Sinica Computing Center, Taiwan

 This software is provided 'as-is', without any express or implied warranty. 
 In no event will the authors be held liable for any damages arising from 
 the use of this software.

 Permission is granted to anyone to use this software for any purpose, 
 including commercial applications, and to alter it and redistribute it freely,
 subject to the following restrictions:

 1. The origin of this software must not be misrepresented; you must not claim
 that you wrote the original software. If you use this software in a product, 
 an acknowledgment in the product documentation would be appreciated but is 
 not required.

 2. Altered source versions must be plainly marked as such, and must not be 
 misrepresented as being the original software.

 3. This notice may not be removed or altered from any source distribution.
-->

<!-- Ideas nabbed from schematrons by Francis N., Miloslav N. and David C. -->

<!-- The command-line parameters are:
            diagnose=yes|no  (default is yes) 
-->

<xsl:stylesheet
   version="1.0"
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:axsl="http://www.w3.org/1999/XSL/TransformAlias"
	xmlns:sch="http://www.ascc.net/xml/schematron"
>

<xsl:import href="skeleton1-5.xsl"/>
<xsl:param name="diagnose">yes</xsl:param>
<xsl:param name="block"></xsl:param><!-- reserved -->
<xsl:param name="phase">
  <xsl:choose>
    <xsl:when test="//sch:schema/@defaultPhase">
      <xsl:value-of select="//sch:schema/@defaultPhase"/>
    </xsl:when>
    <xsl:otherwise>#ALL</xsl:otherwise>
  </xsl:choose>
</xsl:param>

<xsl:template name="process-prolog">
   <axsl:output method="xml" omit-xml-declaration="no" standalone="yes"  indent="yes"/>
</xsl:template>

<xsl:template name="process-root">
   <xsl:param name="title"/>
   <xsl:param name="contents" />
   <xsl:param name="schemaVersion" />
   <!-- unused params: fpi, id, icon, lang, version -->
   <schematron-output title="{$title}" schemaVersion="{$schemaVersion}" 
	phase="{$phase}" >
      <xsl:apply-templates mode="do-schema-p"   />
      <xsl:copy-of select="$contents" />
   </schematron-output>
</xsl:template>

<xsl:template name="process-assert">
   <xsl:param name="id"/>
   <xsl:param name="test" />
   <xsl:param name="role"/>
   <xsl:param name="diagnostics"/>
		<failed-assert  id="{$id}"  test="{$test}" role="{$role}">
			<axsl:attribute name="location"><axsl:apply-templates
			    select="." mode="schematron-get-full-path"/></axsl:attribute>
	         	<xsl:if test="$diagnose = 'yes'">
                           <xsl:call-template name="diagnosticsSplit">
	  			<xsl:with-param name="str" select="$diagnostics"/>
		 	   </xsl:call-template>
                        </xsl:if>
 			<text><xsl:apply-templates mode="text" /></text>
            </failed-assert>
</xsl:template>


<xsl:template name="process-report">
           <xsl:param name="id"/>
		<xsl:param name="test"/>
		<xsl:param name="role"/>
   		<xsl:param name="diagnostics"/>
		<successful-report id="{$id}"  test="{$test}" role="{$role}">
			<axsl:attribute name="location"><axsl:apply-templates
			 select="." mode="schematron-get-full-path"/></axsl:attribute>
        		<xsl:if test="$diagnose = 'yes'">
	         		<xsl:call-template name="diagnosticsSplit">
	  				<xsl:with-param name="str" select="$diagnostics"/>
		 		</xsl:call-template>
                        </xsl:if>
 			<text><xsl:apply-templates mode="text" /></text>
		</successful-report>
	</xsl:template>

<xsl:template name="process-diagnostic">
     <xsl:param name="id"/>
	<diagnostic id="{$id}"><xsl:apply-templates mode="text"/></diagnostic>
</xsl:template>

<xsl:template name="process-rule">
     <xsl:param name="id"/>
	<xsl:param name="context"/>
	<xsl:param name="role"/>
      <fired-rule id="{$id}" context="{$context}" role="{$role}" />
</xsl:template>


<xsl:template name="process-ns">
     <xsl:param name="prefix"/>
	<xsl:param name="uri"/>
  <ns uri="{$uri}" prefix="{$prefix}" />
</xsl:template>

<xsl:template name="process-p">
		<!-- params: pattern, role -->
		<text><xsl:apply-templates mode="text"/></text>
</xsl:template>

<xsl:template name="process-pattern">
     <xsl:param name="name"/>
  <active-pattern name="{$name}"><xsl:apply-templates mode="do-pattern-p"
      /><axsl:apply-templates /></active-pattern>
</xsl:template>

  
<xsl:template name="process-message"/>


</xsl:stylesheet>


