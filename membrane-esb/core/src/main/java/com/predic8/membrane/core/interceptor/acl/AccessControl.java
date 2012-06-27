/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.config.AbstractConfigElement;

public class AccessControl extends AbstractConfigElement {

	public static final String ELEMENT_NAME = "accessControl";
		
	private List<Resource> resources = new ArrayList<Resource>();
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (Resource.ELEMENT_NAME.equals(child)) {
			resources.add((Resource) (new Resource()).parse(token));
		} 
	}

	public List<Resource> getResources() {
		return resources;
	}
	
	public Resource getResourceFor(String uri) throws Exception {
		if (uri == null)
			throw new IllegalArgumentException("Resource URI can not be null.");
		
		for (Resource res : resources) {
			if (res.matches(uri))
				return res;
		}
		throw new IllegalArgumentException("Resource not found for given path");
	}
	
}
