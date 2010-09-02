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


/**
 * 
 * Main class for memrouter.bat file
 * 
 * @author predic8
 *
 */
public class Starter {

	public static void main(String[] args) throws Exception {
		getMainMethod().invoke(null, new Object[] { args });
	}

	private static Method getMainMethod() throws NoSuchMethodException,
			ClassNotFoundException {
		return getRouterCLIClass().getDeclaredMethod("main", new Class[] { String[].class });
	}

	private static Class<?> getRouterCLIClass() throws ClassNotFoundException {
		return getLoader().loadClass("com.predic8.membrane.core.RouterCLI");
	}

	private static URLClassLoader getLoader() {
		return ClassloaderUtil.getExternalClassloader("file:" + System.getenv("MEMBRANE_HOME"));
	}

}
