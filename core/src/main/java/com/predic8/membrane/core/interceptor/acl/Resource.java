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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.config.GenericComplexElement;
import com.predic8.membrane.core.util.TextUtil;

public class Resource extends AbstractXmlElement {

	private static final Logger log = LoggerFactory.getLogger(Resource.class.getName());

	public static final String ELEMENT_NAME = "resource";

	private final Router router;
	private final List<AbstractClientAddress> clientAddresses = new ArrayList<>();

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

	public boolean checkAccess(String hostname, String ip) {
		if (log.isDebugEnabled()) {
			log.debug("Hostname: " + hostname + (router.getTransport().isReverseDNS() ? "" : " (reverse DNS is disabled in configuration)"));
			log.debug("IP: " + ip);
			try {
				log.debug("Hostaddress (might require slow DNS lookup): " + router.getDnsCache().getHostName(InetAddress.getByName(ip)));
			} catch (UnknownHostException e) {
				log.debug("Failed to get hostname from address: " + e.getMessage());
			}
		}

		return  clientAddresses.stream().anyMatch(address -> address.matches(hostname,ip));
	}

	public void addAddress(AbstractClientAddress addr) { clientAddresses.add(addr); }

	public boolean matches(String str) {
		return pattern.matcher(str).matches();
	}

	public String getPattern() {
		return pattern.pattern();
	}

	public void init(Router router) {
		for (AbstractClientAddress ca : clientAddresses)
			ca.init(router);
	}

}
