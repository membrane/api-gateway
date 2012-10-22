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

import com.predic8.membrane.core.config.AbstractXmlElement;

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

	public void setUrl(String url) {
		this.url = url;
	}

	public String getxPath() {
		return xPath;
	}

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
