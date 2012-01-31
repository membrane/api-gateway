/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.security;

import javax.xml.stream.*;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractConfigElement;

public abstract class Store extends AbstractConfigElement {

	protected String location;
	protected String password;
	protected String format;

	public Store(Router router) {
		super(router);
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		location = token.getAttributeValue("", "location");
		password = token.getAttributeValue("", "password");
		format = token.getAttributeValue("", "format");
		super.parseAttributes(token);
	}

	public String getLocation() {
		return location;
	}

	public String getPassword() {
		return password;
	}
	
	public String getFormat() {
		return format;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(getElementName());
		out.writeAttribute("location", location);
		out.writeAttribute("password", password);
		if (format != null)
			out.writeAttribute("format", format);
		out.writeEndElement();
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public void setFormat(String format) {
		this.format = format;
	}

}
