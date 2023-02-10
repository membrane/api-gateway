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

package com.predic8.membrane.core.util;

import com.fasterxml.jackson.core.*;
import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import javax.mail.internet.*;
import javax.net.ssl.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;

public class Util {

	private static final Logger LOG = LoggerFactory.getLogger(Util.class);

	public static void shutdownOutput(Socket socket) throws IOException {
		if (!(socket instanceof SSLSocket) &&
				!socket.isClosed() &&
				!socket.isOutputShutdown()) {
			socket.shutdownOutput();
		}
	}

	public static void shutdownInput(Socket socket) throws IOException {
		//SSLSocket does not implement shutdown input and output
		if (!(socket instanceof SSLSocket) &&
				!socket.isClosed() &&
				!socket.isInputShutdown()) {
			socket.shutdownInput();
		}
	}

	public static HashMap<String, String> parseSimpleJSONResponse(Response g) throws IOException, ParseException {
		HashMap<String, String> values = new HashMap<>();

		String contentType = g.getHeader().getContentType();
		if (contentType != null && g.getHeader().getContentTypeObject().match(APPLICATION_JSON)) {
			final JsonParser jp = new JsonFactory().createParser(new InputStreamReader(g.getBodyAsStreamDecoded()));
			String name = null;
			while (jp.nextToken() != null) {
				switch (jp.getCurrentToken()) {
				case FIELD_NAME:
					name = jp.getCurrentName();
					break;
				case VALUE_STRING:
					values.put(name, jp.getText());
					break;
				case VALUE_NUMBER_INT:
					values.put(name, "" + jp.getLongValue());
				default:
					break;
				}
			}
		}
		return values;
	}

	private static Method newVirtualThreadPerTaskExecutor;
	private static final AtomicBoolean loggedVirtualThreads = new AtomicBoolean();

	static {
		try {
			if (!"false".equals(System.getProperty("membrane.virtualthreads"))) {
				newVirtualThreadPerTaskExecutor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor", new Class[0]);
			}
		} catch (NoSuchMethodException e) {
		}
	}

	public static ExecutorService createNewThreadPool() {
		if (newVirtualThreadPerTaskExecutor != null) {
			try {
				ExecutorService executorService = (ExecutorService) newVirtualThreadPerTaskExecutor.invoke(null, new Object[0]);
				if (loggedVirtualThreads.compareAndSet(false, true))
					LOG.info("Using virtual threads. (Use -Dmembrane.virtualthreads=false to disable.)");
				return executorService;
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				if (e.getCause() instanceof UnsupportedOperationException) {
					LOG.warn("Using traditional threads instead of virtual threads. Use the JVM option --enable-preview to enable virtual threads. (Or use -Dmembrane.virtualthreads=false to disable this warning.)");
					newVirtualThreadPerTaskExecutor = null;
				} else {
					throw new RuntimeException(e);
				}
			}
		}
		return Executors.newCachedThreadPool();
	}
}