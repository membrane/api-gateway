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
