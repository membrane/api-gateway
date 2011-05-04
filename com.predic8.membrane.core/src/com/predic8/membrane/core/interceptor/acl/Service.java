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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.predic8.membrane.core.Router;

public class Service extends AbstractPatternElement {

	public static final String ELEMENT_NAME = "service";
	
	private List<Ip> ipAddresses = new ArrayList<Ip>();
	
	private List<Hostname> hostNames = new ArrayList<Hostname>();
	
	public Service(Router router) {
		super(router);
	}
	
	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Ip.ELEMENT_NAME.equals(child)) {
			ipAddresses.add(((Ip) (new Ip(router)).parse(token)));
		} else if (Hostname.ELEMENT_NAME.equals(child)) {
			hostNames.add(((Hostname) (new Hostname(router)).parse(token)));
		}
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		pattern = Pattern.compile(token.getAttributeValue("", "path"));
	}
	
	/*
	 * Must be overriden because implementation of super class will reset pattern field
	 * (non-Javadoc)
	 * @see com.predic8.membrane.core.interceptor.acl.AbstractPatternElement#parseCharacters(javax.xml.stream.XMLStreamReader)
	 */
	@Override
	protected void parseCharacters(XMLStreamReader token) throws XMLStreamException {
		
	} 
	
	public List<Ip> getIpAddresses() {
		return ipAddresses;
	}

	public List<Hostname> getHostNames() {
		return hostNames;
	}
	public boolean checkAccess(InetAddress inetAddress) {
		return checkHostAddress(inetAddress.getHostAddress()) || checkHostName(inetAddress.getHostName());
	}
	
	private boolean checkHostName(String name) {
		for (Hostname host : hostNames) {
			if (host.matches(name))
				return true;
		}
		
		return false;
	}
	
	private boolean checkHostAddress(String address) {
		for (Ip ipAddress : ipAddresses) {
			if (ipAddress.matches(address))
				return true;
		}
		return false;
	}
	
}
