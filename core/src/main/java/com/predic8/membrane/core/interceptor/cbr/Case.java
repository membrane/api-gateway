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

import javax.xml.stream.*;

import org.springframework.beans.factory.annotation.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.config.AbstractXmlElement;

@MCElement(name="case", topLevel=false)
public class Case extends AbstractXmlElement {

	private String url;
	private String xPath;
	
	public Case() {}
	
	public Case(String xPath, String url) {
		this.url = url;
		this.xPath = xPath;
	}

	public String getUrl() {
		return url;
	}

	/**
	 * @description Target URL
	 * @example http://predic8.com/fastorder
	 */
	@MCAttribute
	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getService() {
		if (url.startsWith("service:"))
			return url.substring(8);
		return null;
	}
	
	@MCAttribute
	public void setService(String service) {
		url = "service:" + service;
	}

	public String getxPath() {
		return xPath;
	}

	/**
	 * @description XPath expression.
	 * @example //fastorder/
	 */
	@Required
	@MCAttribute
	public void setxPath(String xPath) {
		this.xPath = xPath;
	}

	@Override
	public void write(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("case");
		
		out.writeAttribute("xPath", xPath);		
		out.writeAttribute("url", url);		

		out.writeEndElement();
	}
		
	@Override
	protected void parseAttributes(XMLStreamReader token)
			throws XMLStreamException {
		
		xPath = token.getAttributeValue("", "xPath");
		
		url = token.getAttributeValue("", "url");
		
		if (token.getAttributeValue("", "service") != null ) {
			url = "service:"+token.getAttributeValue("", "service");
		}
	}
}
