package com.predic8.membrane.core;

import java.lang.reflect.Method;
import java.net.URLClassLoader;

public class Starter {

	public static void main(String[] args) {
		
		try {
			Class clazz = getLoader().loadClass("com.predic8.membrane.core.RouterCLI");
					
			Method mainMethod = clazz.getDeclaredMethod("main", new Class[]{ String[].class }); 
		
			mainMethod.invoke(null, new Object[] { args });
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	
	private static URLClassLoader getLoader() {
		return ClassloaderUtil.getExternalClassloader("file:" + System.getenv("MEMROUTER_HOME"));
	}
	
}
