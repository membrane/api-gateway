/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.cache;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.*;

import java.io.*;
import java.text.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;

/**
 * @description <p>
 *              Don't use, this does NOT implement valid HTTP caching.
 *              </p>
 *              <p>
 *                  We currently just use this class to cache a bunch of Debian and Ubuntu Repositories as well as
 *                  the Docker Registry for offline use.
 *                  The cache does not revalidate any responses, so machines querying the cache for Debian
 *                  package updates will be stuck in the past until the cache (on disk) is cleared manually. -
 *                  This is - simply put - the only use case, where using this class makes any sense.
 *              </p>
 * @topic 4. Interceptors/Feature	s
 */
@MCElement(name="cache")
public class CacheInterceptor extends AbstractInterceptor {

	static final Logger log = LoggerFactory.getLogger(CacheInterceptor.class.getName());

	private Store store;

	public static abstract class Store {
		public void init(Router router) {}

		public abstract Node get(String url);
		public abstract void put(String url, Node node);
	}

	@MCElement(name="inMemoryStore")
	public static class InMemoryStore extends Store {
		HashMap<String, Node> cache = new HashMap<>();

		@Override
		public Node get(String url) {
			return cache.get(url);
		}

		@Override
		public void put(String url, Node node) {
			cache.put(url, node);
		}
	}

	@MCElement(name="fileStore")
	public static class FileStore extends Store {
		private String dir;

		public String getDir() {
			return dir;
		}

		@MCAttribute
		public void setDir(String dir) {
			this.dir = dir;
		}

		@Override
		public void init(Router router) {
			dir = ResolverMap.combine(router.getBaseLocation(), dir);
			File d = new File(dir);
			if (!d.exists())
				if (!d.mkdirs())
					throw new RuntimeException("Could not create directory " + dir);
		}

		private String encode(String url) {
			String res = Base64.encodeBase64String(url.getBytes(UTF_8));
			if (res.length() > 120) {
				res = res.substring(0, 100) + "-" + res.hashCode();
			}
			return res;
		}

		@Override
		public Node get(String url) {
			File f = new File(dir, encode(url));
			if (!f.exists())
				return null;
			try {
				try(FileInputStream fis = new FileInputStream(f)) {
					return (Node) new ObjectInputStream(fis).readObject();
				}
			} catch (Exception e) {
				log.warn("", e);
				return null;
			}
		}

		@Override
		public void put(String url, Node node) {
			try {
				ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(dir, encode(url))));
				oos.writeObject(node);
				oos.close();
			} catch (Exception e) {
				log.warn("", e);
			}
		}
	}

	public Store getStore() {
		return store;
	}

	@Required
	@MCChildElement
	public void setStore(Store store) {
		this.store = store;
	}

	@Override
	public void init(Router router) throws Exception {
		store.init(router);
	}

	/*
	 * HTTP/1.1 200 OK
	 * Date: Tue, 10 Mar 2015 13:10:30 GMT
	 * Server: ATS/5.1.1
	 * Last-Modified: Thu, 08 May 2014 14:20:33 GMT
	 * ETag: "a841adf-3a5-4f8e42f154e40"
	 * Accept-Ranges: bytes
	 * Content-Length: 933
	 * Age: 13570
	 * Connection: keep-alive
	 */

	static String toRFC(long timestamp) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return dateFormat.format(new Date(timestamp));
	}

	static long fromRFC(String timestamp) throws ParseException {
		if (timestamp == null)
			return 0;
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		return dateFormat.parse(timestamp).getTime();
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String dest = exc.getDestinations().get(0);
		Node node = store.get(dest);
		if (node != null && node.canSatisfy(exc.getRequest())) {
			exc.setResponse(node.toResponse(exc.getRequest()));
			return Outcome.RETURN;
		}

		if (canCache(exc.getRequest(), true)) {
			// simplify request to allow caching
			exc.getRequest().getHeader().removeFields(Header.IF_MODIFIED_SINCE);
		}

		return super.handleRequest(exc);
	}



	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		try {
			if (canCache(exc.getRequest(), false)) {
				if (canCache(exc.getResponse(), true)) {
					String dest = exc.getDestinations().get(0);
					switch (exc.getResponse().getStatusCode()) {
						case 200 -> store.put(dest, new PositiveNode(exc));
						case 401, 404 -> store.put(dest, new NegativeNode(exc));
						case 301, 302, 307 -> store.put(dest, new PositiveNode(exc));
						default ->
								log.warn("Could not cache HTTP response because of its status code " + exc.getResponse().getStatusCode() + ".");
					}
				}
			}
		} catch (Exception e) {
			log.warn("Exception during cache handling.", e);
		}

		// we drop some headers so the client does not get the idea we support
		// any fancy HTTP features
		// TODO: check whether dropping these headers is valid
		exc.getResponse().getHeader().removeFields(name);
		exc.getResponse().getHeader().removeFields("ETag");
		exc.getResponse().getHeader().removeFields("Accept-Ranges");
		exc.getResponse().getHeader().removeFields("Age");
		exc.getResponse().getHeader().removeFields("Connection");
		exc.getResponse().getHeader().removeFields("Vary");
		exc.getResponse().getHeader().removeFields("Expires");
		exc.getResponse().getHeader().removeFields("Cache-Control");


		return super.handleResponse(exc);
	}

	private boolean force = true;

	private final HashSet<String> allowedRequestHeaders = new HashSet<>();
	private final HashSet<String> allowedResponseHeaders = new HashSet<>();

	{
		allowedRequestHeaders.add("host");
		allowedRequestHeaders.add("cache-control");
		allowedRequestHeaders.add("if-modified-since");
		allowedRequestHeaders.add("user-agent");
		allowedRequestHeaders.add("accept");
		if (force) {
			allowedRequestHeaders.add("accept-encoding");
			allowedRequestHeaders.add("authorization");
			allowedRequestHeaders.add("pragma");
		}
		allowedRequestHeaders.add("referer");

		allowedResponseHeaders.add("date");
		allowedResponseHeaders.add("server");
		allowedResponseHeaders.add("last-modified");
		allowedResponseHeaders.add("etag");
		allowedResponseHeaders.add("accept-ranges");
		allowedResponseHeaders.add("content-length");
		allowedResponseHeaders.add("age");
		allowedResponseHeaders.add("connection");
		allowedResponseHeaders.add("vary");
		allowedResponseHeaders.add("content-type");
		allowedResponseHeaders.add("expires");
		allowedResponseHeaders.add("cache-control");
		allowedResponseHeaders.add("location");
		allowedResponseHeaders.add("link");
		allowedResponseHeaders.add("transfer-encoding");
		allowedResponseHeaders.add("status");
		allowedResponseHeaders.add("content-disposition");
		allowedResponseHeaders.add("content-security-policy");
		allowedResponseHeaders.add("strict-transport-security");
		allowedResponseHeaders.add("via");
		allowedResponseHeaders.add("fastly-debug-digest");
		allowedResponseHeaders.add("access-control-allow-origin");

		if (force) {
			allowedResponseHeaders.add("set-cookie");
			allowedResponseHeaders.add("docker-distribution-api-version");
			allowedResponseHeaders.add("www-authenticate");
			allowedResponseHeaders.add("docker-content-digest");
			allowedResponseHeaders.add("cookie");
		}
	}

	private boolean canCache(Request request, boolean emitWarning) {
		for (HeaderField header : request.getHeader().getAllHeaderFields()) {
			String headerName = header.getHeaderName().toString().toLowerCase(Locale.US);
			if (headerName.startsWith("x-"))
				continue;
			if (!allowedRequestHeaders.contains(headerName)) {
				if (headerName.equals("connection") && Header.CLOSE.equals(header.getValue().toLowerCase(Locale.US)))
					continue;
				if (headerName.equals("connection") && "keep-alive".equals(header.getValue().toLowerCase(Locale.US)))
					continue;
				if (headerName.equals("accept-encoding") && "identity".equals(header.getValue().toLowerCase(Locale.US)))
					continue;
				if (emitWarning)
					log.warn("Could not cache request because of '" + header.getHeaderName() + "' header:\n" + request.getStartLine() + request.getHeader());
				return false;
			}
		}
		return true;
	}

	private boolean canCache(Response response, boolean emitWarning) {
		for (HeaderField header : response.getHeader().getAllHeaderFields()) {
			String headerName = header.getHeaderName().toString().toLowerCase(Locale.US);
			if (headerName.startsWith("x-"))
				continue;
			if (!allowedResponseHeaders.contains(headerName)) {
				if (emitWarning)
					log.warn("Could not cache response because of '" + header.getHeaderName() + "' header:\n" + response.getStartLine() + response.getHeader());
				return false;
			}
		}
		return true;
	}

}
