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

import org.osgi.framework.Bundle;

public class BundleClassLoader extends ClassLoader {

	private final Bundle bundle;
	
	public BundleClassLoader(Bundle bundle) {
		this.bundle = bundle;
	}
	
	@Override
	public URL getResource(String name) {
		return bundle.getResource(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			URL url = bundle.getResource(name);
			if (url == null)
				return null;
			return url.openStream();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return bundle.getResources(name);
	}

	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return bundle.loadClass(name);
	}
	
	

}
