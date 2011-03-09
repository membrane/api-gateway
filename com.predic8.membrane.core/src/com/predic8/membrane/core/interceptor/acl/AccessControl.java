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
package com.predic8.membrane.core.interceptor.acl;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.config.AbstractXMLElement;

public class AccessControl extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "accessControl";
	
	private List<Service> services = new ArrayList<Service>();
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Service.ELEMENT_NAME.equals(child)) {
			services.add((Service) (new Service()).parse(token));
		} 
	}

	public List<Service> getServices() {
		return services;
	}
	
	public Service getServiceFor(String path) throws Exception {
		if (path == null)
			throw new IllegalArgumentException("Path can not be null.");
		
		for (Service service : services) {
			if (service.matches(path))
				return service;
		}
		throw new IllegalArgumentException("Service not found for given path");
	}
	
}
