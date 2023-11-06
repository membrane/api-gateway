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

import com.predic8.membrane.core.Router;

import javax.xml.stream.XMLStreamReader;

import static com.predic8.membrane.core.interceptor.acl.ParseType.GLOB;

public class Ip extends AbstractClientAddress {

	public static final String ELEMENT_NAME = "ip";

	private ParseType type = GLOB;

	public Ip(Router router) {
		super(router);
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		this.type = ParseType.getByOrDefault(token.getAttributeValue(null, "type"));
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	public boolean matches(String hostname, String ip) {
		return type.getMatcher().matches(ip, schema);
	}

}
