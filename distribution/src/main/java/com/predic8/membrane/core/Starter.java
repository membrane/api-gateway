/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.util.*;

import java.lang.reflect.Method;
import java.net.URLClassLoader;

import static java.lang.Integer.parseInt;

/**
 * Main class for memrouter.bat file.
 */
public class Starter {

	public static void main(String[] args) throws Exception {

		if (getJavaVersion() < 17) {
			System.err.println("---------------------------------------");
			System.err.println();
			System.err.println("Wrong Java Version!");
			System.err.println();
			System.err.println("Membrane requires Java 17 or newer. The current Java version is " + System.getProperty("java.version") + ".");
			System.err.println("You can check with:");
			System.err.println();
			System.err.println("java -version");
			if (System.getProperty("os.name").contains("Windows")) {
				System.err.println("echo %JAVA_JOME%");
			} else {
				System.err.println("echo $JAVA_JOME");
			}
			System.err.println("---------------------------------------");
			System.exit(1);
		}

		getMainMethod().invoke(null, new Object[]{args});
	}

	private static Method getMainMethod() throws NoSuchMethodException, ClassNotFoundException {
		return getRouterCLIClass().getDeclaredMethod("main", String[].class);
	}

	private static Class<?> getRouterCLIClass() throws ClassNotFoundException {
		return getLoader().loadClass("com.predic8.membrane.core.RouterCLI");
	}

	private static URLClassLoader getLoader() {
		return ClassloaderUtil.getExternalClassloader("file:" + System.getenv("MEMBRANE_HOME"));
	}

	private static int getJavaVersion() {
		String version = System.getProperty("java.version");
		if(version.startsWith("1."))
			return parseInt(version.substring(2, 3));

		if(version.contains(".")) {
			return parseInt(version.substring(0, version.indexOf(".")));
		}
		return parseInt(version);
	}
}
