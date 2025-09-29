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

import com.google.common.collect.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;

public class Request extends Message {

//	private static final Pattern pattern = Pattern.compile("(.+?) (.+?) HTTP/(.+?)$");
	private static final Pattern pattern = Pattern.compile("^([A-Za-z]+)\\s+(\\S+)\\s+HTTP/(\\d\\.\\d)$");
	private static final Pattern stompPattern = Pattern.compile("^(.+?)$");

	public static final String GET = "GET";
	public static final String POST = "POST";
	public static final String PATCH = "PATCH";
	public static final String HEAD = "HEAD";
	public static final String DELETE = "DELETE";
	public static final String PUT = "PUT";
	@SuppressWarnings("unused")
	public static final String TRACE = "TRACE";
	public static final String CONNECT = "CONNECT";
	public static final String OPTIONS = "OPTIONS";

    public static final String PROPFIND = "PROPFIND";
    public static final String MKCOL = "MKCOL";
    public static final String COPY = "COPY";
    public static final String MOVE = "MOVE";
    public static final String LOCK = "LOCK";
    public static final String UNLOCK = "UNLOCK";

	private static final HashSet<String> methodsWithoutBody = Sets.newHashSet(GET, HEAD, CONNECT);

    private static final HashSet<String> methodsWithOptionalBody = Sets.newHashSet(
            DELETE,
			/* some WebDAV methods, see http://www.ietf.org/rfc/rfc2518.txt */
            OPTIONS,
            PROPFIND,
            MKCOL,
            COPY,
            MOVE,
            LOCK,
            UNLOCK);


	String method;
	String uri;

	@Override
	protected void parseStartLine(InputStream in) throws IOException {
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
			if (e.getLineSoFar().isEmpty())
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
		return method + " " + uri + " HTTP/" + version + CRLF;
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isHEADRequest() {
		return HEAD.equals(method);
	}

	public boolean isGETRequest() {
		return GET.equals(method);
	}

	public boolean isPOSTRequest() {
		return POST.equals(method);
	}

	public boolean isDELETERequest() {
		return DELETE.equals(method);
	}

	public boolean isCONNECTRequest() {
		return CONNECT.equals(method);
	}

	public boolean isOPTIONSRequest() {
		return OPTIONS.equals(method);
	}

	@Override
	public String getName() {
		return uri;
	}

	@Override
	public boolean shouldNotContainBody() {
		if (methodsWithoutBody.contains(method))
			return true;
		if (methodsWithOptionalBody.contains(method)) {
			if (header.hasContentLength())
				return header.getContentLength() == 0;
            return header.getFirstValue(TRANSFER_ENCODING) == null;
        }
    
		return false;
	}

	/**
	 * NTLM and SPNEGO authentication schemes authorize HTTP connections, not single requests.
	 * We therefore have to "bind" the targetConnection to the incoming connection to ensure
	 * the same targetConnection is used again for further requests.
	 */
	public boolean isBindTargetConnectionToIncoming() {
		String auth = header.getFirstValue(AUTHORIZATION);
		return auth != null && (auth.startsWith("NTLM") || auth.startsWith("Negotiate"));
	}

	@Override
	public int estimateHeapSize() {
		return super.estimateHeapSize() +
				12 +
				(method != null ? 2*method.length() : 0) +
				(uri != null ? 2*uri.length() : 0);
	}

	@Override
	public <T extends Message> T createSnapshot(Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) {
		Request result = createMessageSnapshot(new Request(), bodyUpdatedCallback, strategy, limit);
		result.setUri(getUri());
		result.setMethod(getMethod());
		return (T) result;
	}

	public final void writeSTOMP(OutputStream out, boolean retainBody) throws IOException {
		out.write(getMethod().getBytes(UTF_8));
		out.write(10);
		for (HeaderField hf : header.getAllHeaderFields())
			out.write((hf.getHeaderName().toString() + ":" + hf.getValue() + "\n").getBytes(UTF_8));
		out.write(10);
		body.write(new PlainBodyTransferer(out), retainBody);
	}

	public static Builder get(String url) throws URISyntaxException {
		return new Builder().get(url);
	}

	public static Builder put(String url) throws URISyntaxException {
		return new Builder().put(url);
	}

	public static Builder post(String url) throws URISyntaxException {
		return new Builder().post(url);
	}

	public static Builder delete(String url) throws URISyntaxException {
		return new Builder().delete(url);
	}

	public static Builder options(String url) throws URISyntaxException {
		return new Builder().options(url);
	}

	public static Builder connect(String url) throws URISyntaxException {
		return new Builder().connect(url);
	}

	public static class Builder {
		private final Request req;
		private String fullURL;

		public Builder() {
			req = new Request();
			req.setVersion("1.1");
		}

		public Request build() {
			return req;
		}

		public Exchange buildExchange() {
			return buildExchange(null);
		}

		public Exchange buildExchange(AbstractHttpHandler handler) {
			Exchange exc = new Exchange(handler);
			Request req = build();
			exc.setRequest(req);
			exc.getDestinations().add(fullURL);
			exc.setOriginalRequestUri(req.getUri());
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

		public Builder authorization(String username, String password) {
			req.getHeader().setAuthorization(username,password);
			return this;
		}

		public Builder header(String headerName, String headerValue) {
			req.getHeader().add(headerName, headerValue);
			return this;
		}

		public Builder contentType(String value) {
			req.getHeader().add(CONTENT_TYPE, value);
			return this;
		}

		public Builder header(Header headers) {
			req.setHeader(headers);
			return this;
		}

		public Builder body(String body) {
			req.setBodyContent(body.getBytes(UTF_8));
			return this;
		}

		public Builder body(InputStream is) {
			req.setBody(new Body(is));
			return this;
		}

		public Builder body(byte[] body) {
			req.setBodyContent(body);
			return this;
		}

		public Builder body(long contentLength, InputStream body) {
			req.body = new Body(body, contentLength);
			Header header = req.getHeader();
			header.removeFields(CONTENT_ENCODING);
			header.removeFields(TRANSFER_ENCODING);
			header.setContentLength(contentLength);
			return this;
		}

		public Builder json(String body) {
			req.setBodyContent(body.getBytes(UTF_8));
			req.header.setContentType(APPLICATION_JSON);
			return this;
		}

		public Builder post(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.POST).url(uriFactory, url);
		}

		public Builder post(String url) throws URISyntaxException {
			return post(new URIFactory(), url);
		}

		public Builder get(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(GET).url(uriFactory, url);
		}

		/**
		 * Sets the request's method to "GET" and the URI to the parameter. Uses a standard {@link URIFactory}.
		 */
		public Builder get(String url) throws URISyntaxException {
			return get(new URIFactory(), url);
		}
		
		public Builder delete(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.DELETE).url(uriFactory, url);
		}

		public Builder delete(String url) throws URISyntaxException {
			return delete(new URIFactory(), url);
		}
		
		public Builder put(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.PUT).url(uriFactory, url);
		}

		public Builder put(String url) throws URISyntaxException {
			return put(new URIFactory(), url);
		}

		public Builder options(String url) throws URISyntaxException {
			return options(new URIFactory(), url);
		}

		public Builder connect(String url) throws URISyntaxException {
			req.setMethod(CONNECT);
            req.setUri(new URIFactory().create(url).getAuthority());
			return this;
		}

		public Builder options(URIFactory uriFactory, String url) throws URISyntaxException {
			return method(Request.OPTIONS).url(uriFactory,url);
		}
	}
}
