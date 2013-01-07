/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.cbr;

import static com.predic8.membrane.core.util.SynchronizedXPathFactory.newXPath;

import java.io.StringWriter;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.xpath.XPathConstants;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.InputSource;

import com.googlecode.jatl.Html;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TextUtil;

@MCElement(name="switch", xsd="" +
		"					<xsd:sequence>\r\n" + 
		"						<xsd:element name=\"case\" minOccurs=\"1\" maxOccurs=\"unbounded\">\r\n" + 
		"							<xsd:complexType>\r\n" + 
		"								<xsd:sequence />\r\n" + 
		"								<xsd:attribute name=\"xPath\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"								<xsd:attribute name=\"url\" type=\"xsd:string\" use=\"required\"/>\r\n" + 
		"							</xsd:complexType>\r\n" + 
		"						</xsd:element>\r\n" + 
		"					</xsd:sequence>\r\n" + 
		"")
public class XPathCBRInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(XPathCBRInterceptor.class.getName());
	
	private RouteProvider routeProvider = new DefaultRouteProvider();
	private Map<String, String> namespaces;
	
	public XPathCBRInterceptor() {
		name = "Content Based Router";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().isBodyEmpty()) {
			return Outcome.CONTINUE;
		}
		
		Case r = findRoute(exc.getRequest());		
		if (r == null) {
			return Outcome.CONTINUE;
		}
		log.debug("match found for {"+r.getxPath()+"} routing to {"+ r.getUrl() + "}");
		
		updateDestination(exc, r);
		return Outcome.CONTINUE;
	}

	private void updateDestination(Exchange exc, Case r) {
		exc.setOriginalRequestUri(r.getUrl());		
		exc.getDestinations().clear();
		exc.getDestinations().add(r.getUrl());
	}

	private Case findRoute(Request request) throws Exception {
		for (Case r : routeProvider.getRoutes()) {
			//TODO getBodyAsStream creates ByteArray each call. That could be a performance issue. Using BufferedInputStream did't work, because stream got closed.
			if ( (Boolean) newXPath(namespaces).evaluate(r.getxPath(), new InputSource(request.getBodyAsStream()), XPathConstants.BOOLEAN) ) 
				return r;
			log.debug("no match found for xpath {"+r.getxPath()+"}");
		}			
		return null;			
	}

	public RouteProvider getRouteProvider() {
		return routeProvider;
	}

	public void setRouteProvider(RouteProvider routeProvider) {
		this.routeProvider = routeProvider;
	}

	public Map<String, String> getNamespaces() {
		return namespaces;
	}

	public void setNamespaces(Map<String, String> namespaces) {
		this.namespaces = namespaces;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("switch");
		
		for (Case r : routeProvider.getRoutes()) {
			r.write(out);
		}
		
		out.writeEndElement();
	}
		
	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("case")) {
			Case r = new Case();
			r.parse(token);
			routeProvider.getRoutes().add(r);
		} else {
			super.parseChildren(token, child);
		}	
	}
	
	@Override
	public String getShortDescription() {
		return "Routes incoming requests based on XPath expressions.";
	}
	
	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(getShortDescription());
		sb.append("<br/>");
		StringWriter sw = new StringWriter();
		new Html(sw){{
			text("The requests are routed based on the following rules:");
			table();
				thead();
					tr();
						th().text("XPath").end();
						th().text("URL").end();
					end();
				end();
				tbody();
				for (Case c : routeProvider.getRoutes()) {
					tr();
						td().text(c.getxPath()).end();
						td().raw(TextUtil.linkURL(c.getUrl())).end();
					end();
				}
				end();
			end();
		}};
		sb.append(sw.toString());
		return sb.toString();
	}
	
	@Override
	public String getHelpId() {
		return "switch";
	}

}
