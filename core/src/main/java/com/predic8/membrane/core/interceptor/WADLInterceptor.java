/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import static com.predic8.membrane.core.Constants.WADL_NS;

import java.io.*;

import javax.xml.namespace.QName;
import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.ws.relocator.Relocator;

@MCInterceptor(xsd="" +
		"	<xsd:element name=\"wadlRewriter\">\r\n" + 
		"		<xsd:complexType>\r\n" + 
		"			<xsd:complexContent>\r\n" + 
		"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
		"					<xsd:sequence />\r\n" + 
		"					<xsd:attribute name=\"port\" type=\"xsd:int\" />\r\n" + 
		"					<xsd:attribute name=\"protocol\" type=\"xsd:string\" />\r\n" + 
		"					<xsd:attribute name=\"host\" type=\"xsd:string\" />\r\n" + 
		"				</xsd:extension>\r\n" + 
		"			</xsd:complexContent>\r\n" + 
		"		</xsd:complexType>\r\n" + 
		"	</xsd:element>\r\n" + 
		"")
public class WADLInterceptor extends RelocatingInterceptor {

	private static Log log = LogFactory.getLog(WADLInterceptor.class.getName());

	public WADLInterceptor() {
		name = "WADL Rewriting Interceptor";
		setFlow(Flow.RESPONSE);
	}

	protected void rewrite(Exchange exc) throws Exception, IOException {

		log.debug("Changing endpoint address in WADL");

		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		Relocator relocator = new Relocator(new OutputStreamWriter(stream,
				getCharset(exc)), getLocationProtocol(), getLocationHost(exc),
				getLocationPort(exc), pathRewriter);

		relocator.getRelocatingAttributes().put(
				new QName(WADL_NS, "resources"), "base");
		relocator.getRelocatingAttributes().put(new QName(WADL_NS, "include"),
				"href");

		relocator.relocate(new InputStreamReader(new ByteArrayInputStream(exc
				.getResponse().getBody().getContent()), getCharset(exc)));

		exc.getResponse().setBodyContent(stream.toByteArray());
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("wadlRewriter");

		if (port != null)
			out.writeAttribute("port", port);
		if (host != null)
			out.writeAttribute("host", host);
		if (protocol != null)
			out.writeAttribute("protocol", protocol);

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {

		port = token.getAttributeValue("", "port");
		host = token.getAttributeValue("", "host");
		protocol = token.getAttributeValue("", "protocol");
	}
}
