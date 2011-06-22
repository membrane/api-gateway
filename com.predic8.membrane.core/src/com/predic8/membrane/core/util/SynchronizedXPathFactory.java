package com.predic8.membrane.core.util;

import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;


public class SynchronizedXPathFactory {
	private static XPathFactory fac = XPathFactory.newInstance();  
	
	/*
	 * Used to creat XPath objects, because XPathFactory is not thread-save and re-entrant.
	 */
	public static synchronized XPath newXPath(Map<String, String> namespaces) {
		XPath xPath = fac.newXPath();
		if ( namespaces != null) {
			xPath.setNamespaceContext(new MapNamespaceContext(namespaces));
			return xPath;
		}
		return xPath;
	}
	

}
