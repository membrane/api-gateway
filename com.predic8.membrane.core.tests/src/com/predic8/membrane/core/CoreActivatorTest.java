package com.predic8.membrane.core;

import java.net.URL;
import java.util.List;

import junit.framework.TestCase;

public class CoreActivatorTest extends TestCase {

	CoreActivator activator = new CoreActivator();
	
	@Override
	protected void setUp() throws Exception {
		
		super.setUp();
	}
	
	public void testGetJarUrls() throws Exception {
		
		List<URL> urls = ClassloaderUtil.getJarUrls("lib");
		for (URL url : urls) {
			System.out.println(url);
		}
	}
	
}
