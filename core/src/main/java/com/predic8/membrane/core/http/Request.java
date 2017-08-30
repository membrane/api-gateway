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

package com.predic8.membrane.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.EOFWhileReadingFirstLineException;
import com.predic8.membrane.core.transport.http.EOFWhileReadingLineException;
import com.predic8.membrane.core.transport.http.NoMoreRequestsException;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLUtil;

public class Request extends Message {

	private static final Logger log = LoggerFactory.getLogger(Request.class.getName());
	private static final Pattern pattern = Pattern.compile("(.+?) (.+?) HTTP/(.+?)$");
	private static final Pattern stompPattern = Pattern.compile("^(.+?)$");

	public static final String METHOD_GET = "GET";
	public static final String METHOD_POST = "POST";
	public static final String METHOD_HEAD = "HEAD";
	public static final String METHOD_DELETE = "DELETE";
	public static final String METHOD_PUT = "PUT";
	public static final String METHOD_TRACE = "TRACE";
	public static final String METHOD_CONNECT = "CONNECT";
	public static final String METHOD_OPTIONS = "OPTIONS";

	private static final HashSet<String> methodsWithoutBody = Sets.newHashSet("GET", "HEAD", "CONNECT");
	private static final HashSet<String> methodsWithOptionalBody = Sets.newHashSet(
			"DELETE",
			/* some WebDAV methods, see http://www.ietf.org/rfc/rfc2518.txt */
			"PROPFIND",
			"MKCOL",
			"COPY",
			"MOVE",
			"LOCK",
			"UNLOCK");


	String method;
	String uri;

	@Override
	public void parseStartLine(InputStream in) throws IOException, EndOfStreamException {
		try {
			String firstLine = HttpUtil.readLine(in);
			Matcher matcher = pattern.matcher(firstLine);
			if (matcher.find()) {
				method = matcher.group(1);
				uri = matcher.group(2);
				version = matcher.group(3);
			} else if (stompPattern.matcher(firstLine).find()) {
				method = firstLine;
				uri = "";
				version = "STOMP";
			} else {
				throw new EOFWhileReadingFirstLineException(firstLine);
			}
		} catch (EOFWhileReadingLineException e) {
			if (e.getLineSoFar().length() == 0)
				throw new NoMoreRequestsException(); // happens regularly at the end of a keep-alive connection
			throw new EOFWhileReadingFirstLineException(e.getLineSoFar());
		}
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	/**
	 * @return the "Request-URI" as sent by the client in the first line of the HTTP request (quoting from <a
	 *         href="https://tools.ietf.org/html/rfc2616#page-36">RFC 2616</a>: Request-URI = "*" | absoluteURI |
	 *         abs_path | authority )
	 */
	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public void create(String method, String uri, String protocol, Header header, InputStream in) throws IOException {
		this.method = method;
		this.uri = uri;
		if (!protocol.startsWith("HTTP/"))
			throw new RuntimeException("Unknown protocol '" + protocol + "'");
		this.version = protocol.substring(5);

		this.header = header;

		createBody(in);
	}


	@Override
	public String getStartLine() {
		StringBuilder buf = new StringBuilder();
		buf.append(method);
		buf.append(" ");
		buf.append(uri);
		buf.append(" HTTP/");
		buf.append(version);
		buf.append(Constants.CRLF);
		return buf.toString();
	}

	@Override
	protected void createBody(InputStream in) throws IOException {
		log.debug("createBody");

		if (isBodyEmpty()) {
			log.debug("empty body created");
			body = new EmptyBody();
			return;
		}

		super.createBody(in);
	}

	public boolean isHEADRequest() {
		return METHOD_HEAD.equals(method);
	}

	public boolean isGETRequest() {
		return METHOD_GET.equals(method);
	}

	public boolean isPOSTRequest() {
		return METHOD_POST.equals(method);
	}

	public boolean isDELETERequest() {
		return METHOD_DELETE.equals(method);
	}

	public boolean isCONNECTRequest() {
		return METHOD_CONNECT.equals(method);
	}

	public boolean isOPTIONSRequest() {
		return METHOD_OPTIONS.equals(method);
	}

	@Override
	public String getName() {
		return uri;
	}

	@Override
	public boolean isBodyEmpty() throws IOException {
		if (methodsWithoutBody.contains(method))
			return true;

		if (methodsWithOptionalBody.contains(method)) {
			if (header.hasContentLength())
				return header.getContentLength() == 0;

			if (getBody() instanceof ChunkedBody) {
				return false;
			}
			return true;
		}

		return super.isBodyEmpty();
	}

	/**
	 * NTLM and SPNEGO authentication schemes authorize HTTP connections, not single requests.
	 *
	 * We therefore have to "bind" the targetConnection to the incoming connection to ensure
	 * the same targetConnection is used again for further requests.
	 */
	public boolean isBindTargetConnectionToIncoming() {
		String auth = header.getFirstValue(Header.AUTHORIZATION);
		return auth != null && (auth.startsWith("NTLM") || auth.startsWith("Negotiate"));
	}

	@Override
	public int estimateHeapSize() {
		return super.estimateHeapSize() +
				12 +
				(method != null ? 2*method.length() : 0) +
				(uri != null ? 2*uri.length() : 0);
	}

	public final void writeSTOMP(OutputStream out) throws IOException {
		out.write(getMethod().getBytes(Constants.UTF_8));
		out.write(10);
		for (HeaderField hf : header.getAllHeaderFields())
			out.write((hf.getHeaderName().toString() + ":" + hf.getValue() + "\n").getBytes(Constants.UTF_8));
		out.write(10);
		body.write(new PlainBodyTransferrer(out));
	}


	public static class Builder {
		private Request req;
		private String fullURL;

		public Builder() {
			req = new Request();
			req.setVersion("1.1");
		}

		public Request build() {
			return req;
		}

		public Exchange buildExchange() {
			Exchange exc = new Exchange(null);
			exc.setRequest(build());
			exc.getDestinations().add(fullURL);
			return exc;
		}

		public Builder method(String method) {
			req.setMethod(method);
			return this;
		}

		public Builder url(URIFactory uriFactory, String url) throws URISyntaxException {
			fullURL = url;
			req.setUri(URLUtil.getPathQuery(uriFactory, url));
			return this;
		}

		public Builder header(String headerName, String headerValue) {
			req.getHeader().add(headerName, headerValue);
			return this;
		}

		public Builder contentType(String value) {
			req.getHeader().add(Header.CONTENT_TYPE, value);
			return this;
		}

		public Builder header(Header headers) {
			req.setHeader(headers);
			return this;
		}

		public Builder body(String body) {
			req.setBodyContent(body.getBytes());
			return this;
		}

		public Builder body(byte[] body) {
			req.setBodyContent(body);
			return this;
		}

		public Builder body(long contentLength, InputStream body) throws IOException {
			req.body = new Body(body, contentLength);
			Header header = req.getHeader();
			header.removeFields(Header.CONTENT_ENCODING);
			header.removeFields(Header.TRANSFER_ENCODING);
			header.setContentLength(contentLength);
			return this;
		}

		public Builder post(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.METHOD_POST).url(uriFactory, url);
		}

		public Builder post(String url) throws URISyntaxException {
			return post(new URIFactory(), url);
		}

		public Builder get(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.METHOD_GET).url(uriFactory, url);
		}

		/**
		 * Sets the request's method to "GET" and the URI to the parameter. Uses a standard {@link URIFactory}.
		 */
		public Builder get(String url) throws URISyntaxException {
			return get(new URIFactory(), url);
		}
		
		public Builder delete(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.METHOD_DELETE).url(uriFactory, url);
		}

		public Builder delete(String url) throws URISyntaxException {
			return delete(new URIFactory(), url);
		}
		
		public Builder put(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.METHOD_PUT).url(uriFactory, url);
		}

		public Builder put(String url) throws URISyntaxException {
			return put(new URIFactory(), url);
		}
	}

}
