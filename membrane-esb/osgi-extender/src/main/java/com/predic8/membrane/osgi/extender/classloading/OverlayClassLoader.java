/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.osgi.extender.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;

public class OverlayClassLoader extends ClassLoader {

	private final ClassLoader classLoader1, classLoader2;

	public OverlayClassLoader(ClassLoader classLoader1, ClassLoader classLoader2) {
		this.classLoader1 = classLoader1;
		this.classLoader2 = classLoader2;
	}

	public void clearAssertionStatus() {
		classLoader1.clearAssertionStatus();
	}

	public boolean equals(Object obj) {
		
		return classLoader1.equals(obj);
	}

	public URL getResource(String name) {
		URL result = classLoader1.getResource(name);
		if (result != null)
			return result;
		return classLoader2.getResource(name);
	}

	public InputStream getResourceAsStream(String name) {
		InputStream result = classLoader1.getResourceAsStream(name);
		if (result != null)
			return result;
		return classLoader2.getResourceAsStream(name);
	}

	public Enumeration<URL> getResources(String name) throws IOException {
		Vector<URL> result = new Vector<URL>();
		Enumeration<URL> e = classLoader1.getResources(name);
		if (e != null)
			while (e.hasMoreElements())
				result.add(e.nextElement());
		e = classLoader2.getResources(name);
		if (e != null)
			while (e.hasMoreElements())
				result.add(e.nextElement());
		return result.elements();
	}

	public Class<?> loadClass(String name) throws ClassNotFoundException {
		Class<?> result = null;
		try {
			result = classLoader1.loadClass(name);
		} catch (ClassNotFoundException e) {
			// ignore
		}
		if (result != null)
			return result;
		try {
			return classLoader2.loadClass(name);
		} catch (ClassNotFoundException e) {
			throw new ClassNotFoundException("Class " + name + " not found in either classloaders.");
		}
	}

	public void setClassAssertionStatus(String className, boolean enabled) {
		classLoader1.setClassAssertionStatus(className, enabled);
	}

	public void setDefaultAssertionStatus(boolean enabled) {
		classLoader1.setDefaultAssertionStatus(enabled);
	}

	public void setPackageAssertionStatus(String packageName, boolean enabled) {
		classLoader1.setPackageAssertionStatus(packageName, enabled);
	}

	public String toString() {
		return "overlay(" + classLoader1.toString() + "," + classLoader2.toString() + ")";
	}

}
