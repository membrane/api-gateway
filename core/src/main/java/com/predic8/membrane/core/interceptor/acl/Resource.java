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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractConfigElement;
import com.predic8.membrane.core.config.GenericComplexElement;
import com.predic8.membrane.core.util.TextUtil;

public class Resource extends AbstractConfigElement {

	private static Log log = LogFactory.getLog(Resource.class.getName());
	
	public static final String ELEMENT_NAME = "resource";
	
	private Router router;
	private List<AbstractClientAddress> clientAddresses = new ArrayList<AbstractClientAddress>();
	
	protected Pattern pattern;
	
	public Resource(Router router) {
		this.router = router;
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws Exception {
		if (Ip.ELEMENT_NAME.equals(child)) {
			clientAddresses.add(((Ip) (new Ip(router)).parse(token)));
		} else if (Hostname.ELEMENT_NAME.equals(child)) {
			clientAddresses.add(((Hostname) (new Hostname(router)).parse(token)));
		} else if (Any.ELEMENT_NAME.equals(child)) {
			clientAddresses.add(((Any) (new Any(router)).parse(token)));
		} else if ("clients".equals(child)) {
			new GenericComplexElement(this).parse(token);
		}
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		pattern = Pattern.compile(TextUtil.globToRegExp(token.getAttributeValue(null, "uri")));
	}
	
	public boolean checkAccess(InetAddress inetAddress) {
		if (log.isDebugEnabled()) {
			log.debug("Hostname: " + router.getDnsCache().getHostName(inetAddress));
			log.debug("Canonical Hostname: " + router.getDnsCache().getCanonicalHostName(inetAddress));
			log.debug("Hostaddress: " + inetAddress.getHostAddress()); // slow
		}
		
		for (AbstractClientAddress cAdd : clientAddresses) {
			if (cAdd.matches(inetAddress))
				return true;
		}
		
		return false;
	}
	
	public List<AbstractClientAddress> getClientAddresses() {
		return clientAddresses;
	}

	public boolean matches(String str) {
		return pattern.matcher(str).matches();
	}
	
	public String getPattern() {
		return pattern.pattern();
	}
	
}
