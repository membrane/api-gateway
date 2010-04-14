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
