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

import com.predic8.membrane.core.config.AbstractXMLElement;

public class Service extends AbstractXMLElement {

	public static final String ELEMENT_NAME = "service";
	
	private String path;
	
	private List<String> ipAddresses = new ArrayList<String>();
	
	private List<String> hostNames = new ArrayList<String>();
	

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}
	
	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (Ip.ELEMENT_NAME.equals(child)) {
			ipAddresses.add(((Ip) (new Ip()).parse(token)).getValue());
		} else if (Hostname.ELEMENT_NAME.equals(child)) {
			hostNames.add(((Hostname) (new Hostname()).parse(token)).getValue());
		}
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws XMLStreamException {
		path = token.getAttributeValue("", "path");
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public List<String> getIpAddresses() {
		return ipAddresses;
	}

	public List<String> getHostNames() {
		return hostNames;
	}
	public boolean checkAccess(InetAddress inetAddress) {
		if (accessEnabledForHostAddress(inetAddress.getHostAddress()))
			return true;
		
		if (accessEnabledForHostName(inetAddress.getHostName()))
			return true;
	
		return false;
	}
	

	private boolean accessEnabledForHostName(String name) {
		for (String host : hostNames) {
			if (Pattern.compile(host).matcher(name).matches())
				return true;
		}
		
		return false;
	}
	
	private boolean accessEnabledForHostAddress(String address) {
		for (String ipAddress : ipAddresses) {
			if (Pattern.compile(ipAddress).matcher(address).matches())
				return true;
		}
		return false;
	}
	
}
