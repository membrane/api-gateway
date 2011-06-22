package com.predic8.membrane.core.util;

import javax.xml.xpath.*;

public class SynchronizedXPathFactory {
	private static XPathFactory fac = XPathFactory.newInstance();  
	
	/*
	 * Used to creat XPath objects, because XPathFactory is not thread-save and re-entrant.
	 */
	public static synchronized XPath newXPath() {
		return fac.newXPath();
	}
	

}
