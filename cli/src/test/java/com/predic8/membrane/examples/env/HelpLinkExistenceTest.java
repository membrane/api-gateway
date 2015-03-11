/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.env;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.transport.http.HttpClient;

public class HelpLinkExistenceTest {
	
	private static final String CACHE_FILE_FORMAT_VERSION = "1";

	private String getVersion() {
		String v = Constants.VERSION;
		int p = v.indexOf('.');
		if (p == -1)
			return "current";
		p = v.indexOf('.', p+1);
		if (p == -1)
			return "current";
		return v.substring(0, p);
	}

	@Test
	public void doit() throws Exception {
		Set<Class<?>> classes = getElementClasses();
		
		Assert.assertNotEquals(0, classes.size());
		HttpClient hc = new HttpClient();
		for (Class<?> clazz : classes) {
			if (Interceptor.class.isAssignableFrom(clazz)) {
				Interceptor i = (Interceptor) clazz.newInstance();
				String helpId = i.getHelpId();
				
				String url = "http://membrane-soa.org/service-proxy-doc/" + getVersion() + "/configuration/reference/" + helpId + ".htm";
				
				Response r = hc.call(new Request.Builder().get(url).buildExchange()).getResponse();
				
				try {
					Assert.assertEquals(200, r.getStatusCode());
				} catch (Exception e) {
					throw new RuntimeException(url, e);
				}
			}
		}

	}
	
	@SuppressWarnings("unchecked")
	private Set<Class<?>> getElementClasses() {
		try {
			HashSet<Class<?>> currentSet = new HashSet<Class<?>>();

			BufferedReader r = new BufferedReader(new InputStreamReader(Router.class.getResourceAsStream("/META-INF/membrane.cache")));
			try {
				if (!CACHE_FILE_FORMAT_VERSION.equals(r.readLine()))
					throw new RuntimeException();
				Class<? extends Annotation> annotationClass = null;
				boolean collecting = false;
				while (true) {
					String line = r.readLine();
					if (line == null)
						break;
					if (line.startsWith(" ")) {
						if (collecting) {
							line = line.substring(1);
							Class<?> clazz;
							try {
								clazz = Class.forName(line);
								currentSet.add(clazz);
							} catch (ClassNotFoundException e) {
								// do nothing
							}
						}
					} else {
						try {
							annotationClass = (Class<? extends Annotation>) getClass().getClassLoader().loadClass(line);
						} catch (ClassNotFoundException e) {
							throw new RuntimeException(e);
						}
						collecting = annotationClass.equals(MCElement.class);
					}
				}
			} finally {
				r.close();
			}
			return currentSet;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
