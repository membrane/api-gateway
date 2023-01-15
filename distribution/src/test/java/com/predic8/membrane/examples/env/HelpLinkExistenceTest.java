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
import java.util.*;

import org.junit.jupiter.api.Test;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.transport.http.HttpClient;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This test unfortunately fails if there are new classes in the project
 * for which there is no documentation available in the online docs yet!
 */
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

		assertNotEquals(0, classes.size());
		HttpClient hc = new HttpClient();
		StringBuilder errors = new StringBuilder();
		for (Class<?> clazz : classes) {
			if (Interceptor.class.isAssignableFrom(clazz)) {
				Interceptor i = (Interceptor) clazz.getDeclaredConstructor().newInstance();
				String helpId = i.getHelpId();

				Response r = hc.call(new Request.Builder().get(getDocumentationReferenceURL(helpId)).buildExchange()).getResponse();

				if (r.getStatusCode() != 200)
					errors.append(getDocumentationReferenceURL(helpId))
							.append(" returned ")
							.append(r.getStatusCode())
							.append(".")
							.append(System.lineSeparator());
			}
		}
		assertEquals(0, errors.length(), errors.toString());
	}

	private String getDocumentationReferenceURL(String helpId) {
		return "http://membrane-soa.org/service-proxy-doc/" + getVersion() + "/configuration/reference/" + helpId + ".htm";
	}

	@SuppressWarnings("unchecked")
	private Set<Class<?>> getElementClasses() {
		try {
			HashSet<Class<?>> currentSet = new HashSet<>();

			try (BufferedReader r = new BufferedReader(new InputStreamReader(requireNonNull(Router.class.getResourceAsStream("/META-INF/membrane.cache"))))) {
				if (!CACHE_FILE_FORMAT_VERSION.equals(r.readLine()))
					throw new RuntimeException();
				Class<? extends Annotation> annotationClass;
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
			}
			return currentSet;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
