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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractConfigElement;

public abstract class Store extends AbstractConfigElement {

	protected String location;
	
	protected String password;
	
	public Store(Router router) {
		super(router);
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (Location.ELEMENT_NAME.equals(child)) {
			location = ((Location) new Location().parse(token)).getValue();
		} 
		
		if (Password.ELEMENT_NAME.equals(child)) {
			password = ((Password) new Password().parse(token)).getValue();
		} 
	}
	
	public String getLocation() {
		return location;
	}
	
	public String getPassword() {
		return password;
	}
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(getElementName());
		
		new Location(location).write(out);
		new Password(password).write(out);
		
		out.writeEndElement();
	}
	
	public void setLocation(String location) {
		this.location = location;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
}
