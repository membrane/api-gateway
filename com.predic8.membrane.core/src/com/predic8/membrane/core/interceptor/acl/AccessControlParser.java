package com.predic8.membrane.core.interceptor.acl;

import java.io.FileInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

public class AccessControlParser {

	public AccessControl read(String fileName) throws Exception {
		
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    FileInputStream fis = new FileInputStream(fileName);
	    XMLStreamReader reader = factory.createXMLStreamReader(fis);
	    
	    return (AccessControl)new AccessControl().parse(reader);
	}
	
}
