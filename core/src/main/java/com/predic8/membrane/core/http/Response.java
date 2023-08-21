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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.util.*;
import org.apache.commons.text.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.ResponseBuilder.*;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.text.StringEscapeUtils.*;

public class Response extends Message {

	private static final Logger log = LoggerFactory.getLogger(Response.class.getName());

	private static final ObjectMapper om = new ObjectMapper();

	private static final Pattern pattern = Pattern.compile("HTTP/(\\d\\.\\d) (\\d\\d\\d)( (.*?))?$");

	private int statusCode;
	private String statusMessage;

	public static class ResponseBuilder {
		private final Response res = new Response();

		public Response build() {
			return res;
		}

		public ResponseBuilder status(int code) {
			res.setStatusCode(code);
			res.setStatusMessage(getMessageForStatusCode(code));
			return this;
		}

		public ResponseBuilder status(int code, String msg) {
			res.setStatusCode(code);
			res.setStatusMessage(msg);
			return this;
		}

		/**
		 * Supposes UTF8 encoding.
		 */
		public ResponseBuilder body(String msg) {
			res.setBodyContent(msg.getBytes(UTF_8));
			return this;
		}

		/**
		 * Used for returning JSON from JavascriptInterceptor
		 * JSON MAP
		 */
		public ResponseBuilder body(Map<String,Object> map) throws JsonProcessingException {
			res.setBodyContent(om.writeValueAsBytes(map));
			res.getHeader().setContentType(APPLICATION_JSON);
			return this;
		}

		public ResponseBuilder body(byte[] body) {
			res.setBodyContent(body);
			return this;
		}

		private static class BodyCompleteMessageObserver extends AbstractMessageObserver implements NonRelevantBodyObserver{

			private final InputStream stream;

			public BodyCompleteMessageObserver(InputStream stream) {
				this.stream = stream;
			}

			@Override
			public void bodyComplete(AbstractBody body) {
				try {
					stream.close();
				} catch (IOException e) {
					log.error("Could not close body stream.", e);
				}
			}
		}

		public ResponseBuilder body(final InputStream stream, boolean closeStreamWhenDone) throws IOException {
			// use chunking, since Content-Length is not known
			res.getHeader().removeFields(CONTENT_LENGTH);
			res.getHeader().setValue(TRANSFER_ENCODING, CHUNKED);
			Body b = new Body(stream);
			if (closeStreamWhenDone) {
				b.addObserver(new BodyCompleteMessageObserver(stream));
			}
			res.setBody(b);
			return this;
		}

		public ResponseBuilder bodyEmpty() {
			res.getHeader().setContentLength(0);
			return this;
		}

		public ResponseBuilder header(Header header) {
			res.setHeader(header);
			return this;
		}

		public ResponseBuilder header(String k, String v) {
			res.getHeader().add(k, v);
			return this;
		}

		public ResponseBuilder contentType(String type) {
			res.getHeader().setContentType(type);
			return this;
		}

		public ResponseBuilder location(String location) {
			res.getHeader().setLocation(location);
			return this;
		}

		public static ResponseBuilder newInstance() {
			return new ResponseBuilder();
		}

		public ResponseBuilder dontCache() {
			res.getHeader().setNoCacheResponseHeaders();
			return this;
		}
	}

	public static ResponseBuilder ok(String msg) throws Exception {
		return ok().contentType(TEXT_HTML_UTF8).body(msg);
	}

	private static final String SERVER_HEADER = PRODUCT_NAME;

	public static ResponseBuilder statusCode(int statusCode) {
		return newInstance().status(statusCode);
	}

	public static ResponseBuilder ok() {
		return newInstance().status(200).header("Server", SERVER_HEADER).bodyEmpty();
	}

	public static ResponseBuilder noContent() {
		return newInstance().status(204);
	}

	public static ResponseBuilder found(String location) {
		return ResponseBuilder.newInstance().
				status(302, "Found").
				header(LOCATION, location).
				bodyEmpty();
	}

	public static ResponseBuilder notModified(String date) {
		return newInstance().
				status(304).
				header("Server", SERVER_HEADER).
				header("Date", date).
				bodyEmpty();
	}

	public static ResponseBuilder badRequest() {
		return newInstance().
				status(400).
				header("Server", SERVER_HEADER).
				bodyEmpty();
	}

	public static ResponseBuilder badRequest(String message) {
		return fromStatusCode(400,message).header(SERVER,SERVER_HEADER);
	}

	public static ResponseBuilder badRequest(String message, boolean escape) {
		return newInstance().
				status(400).
				header("Server", SERVER_HEADER).
				contentType(TEXT_HTML_UTF8).
				body(escape ? htmlMessage("Bad Request", message) : unescapedHtmlMessage("Bad Request", message));
	}

	public static ResponseBuilder continue100() {
		return newInstance().status(100);
	}

	public static ResponseBuilder redirect(String uri, boolean permanent) {
		String escaped = StringEscapeUtils.escapeXml11(uri);
		return ResponseBuilder.newInstance().
				status(permanent ? 301 : 307, permanent ? "Moved Permanently" : "Temporary Redirect").
				header(LOCATION, uri).
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.unescapedHtmlMessage("Moved.", "This page has moved to <a href=\""+escaped+"\">"+escaped+"</a>."));
	}

	public static ResponseBuilder redirectWithout300(String uri) {
		String escaped = escapeXml11(uri);
		return redirectWithout300(uri, """
				This page has moved to <a href="%s">%s</a>
				""".formatted(escaped,escaped));
	}

	public static ResponseBuilder redirectWithout300(String uri, String body) {
		return newInstance()
				.status(200)
				.contentType(TEXT_HTML_UTF8)
				.location(uri)
				.body("""
					<html>
					  <head><meta http-equiv="refresh" content="0;URL='%s'"/></head>
					  <body>%s</body>
					</html>
				""".formatted(escapeXml11(uri),body));
	}

	public static ResponseBuilder serviceUnavailable(String message) {
		return fromStatusCode(503,message);
	}

	public static ResponseBuilder internalServerError() {
		return fromStatusCode(500,"");
	}

	@SuppressWarnings("unused")
	public static ResponseBuilder notImplemented() {
		return fromStatusCode(501,"");
	}

	public static ResponseBuilder internalServerError(String message) {
		return fromStatusCode(500,message);
	}

	public static ResponseBuilder badGateway(String message) {
		return fromStatusCode(502,message);
	}

	@SuppressWarnings("unused")
	public static ResponseBuilder gatewayTimeout(String message) {
		return fromStatusCode(504,message);
	}

	public static ResponseBuilder forbidden() {
		return fromStatusCode(403,"");
	}

	public static ResponseBuilder notFound() {
		return fromStatusCode(404,"");
	}

	public static ResponseBuilder forbidden(String message) {
		return fromStatusCode(403,message);
	}

	public static ResponseBuilder unauthorized(String message) {
		return fromStatusCode(401, message);
	}

	public static ResponseBuilder fromStatusCode(int statusCode, String msg) {
		return newInstance().
				status(statusCode).
				contentType(TEXT_HTML_UTF8).
				body(unescapedHtmlMessage("%d %s.".formatted(statusCode, getMessageForStatusCode(statusCode)), msg));
	}

	public static ResponseBuilder unauthorized() {
		return newInstance().
				status(401).
				contentType(TEXT_HTML_UTF8).
				bodyEmpty();
	}

	public static ResponseBuilder methodNotAllowed() {
		return newInstance().
				status(405).
				contentType(TEXT_HTML_UTF8).
				contentType(MimeType.TEXT_HTML_UTF8).
				body(htmlMessage("405 Method Not Allowed", ""));
	}

	@Override
	public String getStartLine() {
		return "HTTP/" +
				version +
				" " +
				statusCode +
				" " +
				statusMessage +
				CRLF;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}

	@Override
	public void parseStartLine(InputStream in) throws IOException,
	EndOfStreamException {

		String line;
		try {
			line = readLine(in);
		} catch (EOFWhileReadingLineException e) {
			if (e.getLineSoFar().length() == 0)
				throw new NoResponseException(e);
			throw new EOFWhileReadingFirstLineException(e.getLineSoFar());
		}

		Matcher matcher = pattern.matcher(line);

		if (!matcher.find()) {
			throw new RuntimeException("Invalid server response: " + line);
		}
		version = matcher.group(1);
		statusCode = parseInt(matcher.group(2));
		statusMessage = matcher.group(4);
	}

	@Override
	public void read(InputStream in, boolean createBody) throws IOException,
	EndOfStreamException {
		parseStartLine(in);

		if (getStatusCode() == 100) {
			readLine(in);
			return;
		}

		header = new Header(in);

		if (createBody)
			createBody(in);
	}

	@Override
	protected void createBody(InputStream in) throws IOException {
		if (isRedirect() && mayHaveNoBody())
			return;

		super.createBody(in);
	}

	public boolean isRedirect() {
		return statusCode >= 300 && statusCode < 400;
	}

	@SuppressWarnings("unused")
	public boolean hasNoContent() {
		return statusCode == 204;
	}

	@Override
	public String getName() {
		return " " + statusCode;
	}

	public boolean shouldNotContainBody()  {
		return statusCode == 100 || statusCode == 101 || statusCode == 204 || statusCode == 205 ;
	}

	public boolean isOk(){
		return statusCode >= 200 && statusCode < 300;
	}

	public boolean isUserError() {
		return statusCode >= 400 && statusCode < 500;
	}

	@SuppressWarnings("unused")
	public boolean isServerError() {
		return statusCode >= 500;
	}

	/**
	 * Some web servers may not send a body e.g. after a redirect. We therefore
	 * do not parse it in {@link #createBody(InputStream)} and close the connection
	 * even when it is keep-alive.
	 */
	private boolean mayHaveNoBody() {
		if (header.isChunked())
			return false;
		if (header.hasContentLength())
			return false;
		return header.getContentType() == null;
	}

	@Override
	public boolean isKeepAlive() {
		if (isRedirect() && mayHaveNoBody())
			return false;
		return super.isKeepAlive();
	}

	@Override
	public int estimateHeapSize() {
		return super.estimateHeapSize() +
				12 +
				(statusMessage != null ? 2*statusMessage.length() : 0);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends Message> T createSnapshot(Runnable bodyUpdatedCallback, BodyCollectingMessageObserver.Strategy strategy, long limit) {
		Response result = this.createMessageSnapshot(new Response(), bodyUpdatedCallback, strategy, limit);

		result.setStatusCode(this.getStatusCode());
		result.setStatusMessage(this.getStatusMessage());

		return (T) result;
	}
}