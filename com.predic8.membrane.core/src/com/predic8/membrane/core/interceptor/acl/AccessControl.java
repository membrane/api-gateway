package com.predic8.membrane.core.interceptor.acl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
	
	public Service getServiceFor(String path) {
		if (path == null)
			return null;
		
		for (Service service : services) {
			if (Pattern.compile(service.getPath()).matcher(path).matches())
				return service;
		}
		return null;
	}
	
}
