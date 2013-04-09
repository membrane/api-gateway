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

import java.net.InetAddress;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.util.TextUtil;

public abstract class AbstractClientAddress extends AbstractXmlElement {

	protected Router router;
	protected Pattern pattern;
	
	public AbstractClientAddress(Router router) {
		super();
		this.router = router;
	}
	
	public abstract boolean matches(InetAddress str);
	
	@Override
	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		setPattern(TextUtil.globToRegExp(token.getText()));
	}
	
	@Override
	public String toString() {
		return pattern.pattern();
	}
	
	public void setPattern(String pattern) {
		this.pattern = Pattern.compile(pattern);
	}
	
}
