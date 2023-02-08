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
import static com.predic8.membrane.core.http.MimeType.*;
import static java.nio.charset.StandardCharsets.*;

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
			res.setStatusMessage(HttpUtil.getMessageForStatusCode(code));
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
		 *
		 * @throws JsonProcessingException
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
			res.getHeader().removeFields(Header.CONTENT_LENGTH);
			res.getHeader().setValue(Header.TRANSFER_ENCODING, Header.CHUNKED);
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

	private static final String SERVER_HEADER = PRODUCT_NAME + " " + VERSION + ". See http://membrane-soa.org";

	public static ResponseBuilder ok() {
		return ResponseBuilder.newInstance().
				status(200, "Ok").
				header("Server", SERVER_HEADER).
				bodyEmpty();
	}

	public static ResponseBuilder noContent() {
		return ResponseBuilder.newInstance().
				status(204, "No Content").
				bodyEmpty();
	}

	public static ResponseBuilder notModified(String date) {
		return ResponseBuilder.newInstance().
				status(304, "Not Modified").
				header("Server", SERVER_HEADER).
				header("Date", date).
				bodyEmpty();
	}

	public static ResponseBuilder badRequest() {
		return ResponseBuilder.newInstance().
				status(400, "Bad Request").
				header("Server", SERVER_HEADER).
				bodyEmpty();
	}

	public static ResponseBuilder badRequest(String message) {
		return ResponseBuilder.newInstance().
				status(400, "Bad Request").
				header("Server", SERVER_HEADER).
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Bad Request", message));
	}

	public static ResponseBuilder badRequest(String message, boolean escape) {
		return ResponseBuilder.newInstance().
				status(400, "Bad Request").
				header("Server", SERVER_HEADER).
				contentType(TEXT_HTML_UTF8).
				body(escape ? HttpUtil.htmlMessage("Bad Request", message) : HttpUtil.unescapedHtmlMessage("Bad Request", message));
	}

	public static ResponseBuilder continue100() {
		return ResponseBuilder.newInstance().
				status(100, "Continue");
	}

	public static ResponseBuilder redirect(String uri, boolean permanent) {
		String escaped = StringEscapeUtils.escapeXml11(uri);
		return ResponseBuilder.newInstance().
				status(permanent ? 301 : 307, permanent ? "Moved Permanently" : "Temporary Redirect").
				header("Location", uri).
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.unescapedHtmlMessage("Moved.", "This page has moved to <a href=\""+escaped+"\">"+escaped+"</a>."));
	}

	public static ResponseBuilder redirectGet(String uri){
		String escaped = StringEscapeUtils.escapeXml11(uri);
		return ResponseBuilder.newInstance().
				status(303, "Temporary Redirect").
				header("Location", uri).
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.unescapedHtmlMessage("Moved.", "This page has moved to <a href=\""+escaped+"\">"+escaped+"</a>."));
	}

	public static ResponseBuilder redirectWithout300(String uri) {
		String escaped = StringEscapeUtils.escapeXml11(uri);
		return redirectWithout300(uri, " This page has moved to <a href=\"" + escaped + "\">" + escaped + "</a>.");
	}

	public static ResponseBuilder redirectWithout300(String uri, String body) {
		String escaped = StringEscapeUtils.escapeXml11(uri);
		return ResponseBuilder.newInstance().
				status(200, "OK").
				header("Location", uri).
				contentType(TEXT_HTML_UTF8).
				body("<html><head><meta http-equiv=\"refresh\" content=\"0;URL='" + escaped + "'\" /></head>" +
						"<body>" +
						body +
						"</body>");
	}

	public static ResponseBuilder serverUnavailable(String message) {
		return ResponseBuilder.newInstance().
				status(503, "Service Unavailable").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Service Unavailable", message));
	}

	public static ResponseBuilder internalServerError() {
		return ResponseBuilder.newInstance().
				status(500, "Internal Server Error").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Internal Server Error", ""));
	}

	public static ResponseBuilder notImplemented() {
		return ResponseBuilder.newInstance().
				status(501, "	Not Implemented").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Not Implemented", ""));
	}

	public static ResponseBuilder internalServerError(String message) {
		return ResponseBuilder.newInstance().
				status(500, "Internal Server Error").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Internal Server Error", message));
	}

	public static ResponseBuilder badGateway(String message) {
		return ResponseBuilder.newInstance().
				status(502, "Bad Gateway").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Bad Gateway", message));
	}

	public static ResponseBuilder gatewayTimeout(String message) {
		return ResponseBuilder.newInstance().
				status(504, "Gateway timeout").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Gateway timeout", message));
	}

	public static ResponseBuilder forbidden() {
		return ResponseBuilder.newInstance().
				status(403, "Forbidden").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Forbidden", ""));
	}

	public static ResponseBuilder notFound() {
		return ResponseBuilder.newInstance().
				status(404, "Not Found").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("404 Page Not Found", ""));
	}

	public static ResponseBuilder forbidden(String message) {
		return ResponseBuilder.newInstance().
				status(403, "Forbidden").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Forbidden", message));
	}

	public static ResponseBuilder unauthorized(String message) {
		return ResponseBuilder.newInstance().
				status(401, "Unauthorized.").
				contentType(TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("Unauthorized.", message));
	}

	public static ResponseBuilder unauthorized() {
		return ResponseBuilder.newInstance().
				status(401, "Unauthorized.").
				contentType(TEXT_HTML_UTF8).
				bodyEmpty();
	}

	public static ResponseBuilder methodNotAllowed() {
		return ResponseBuilder.newInstance().
				status(405).
				contentType(TEXT_HTML_UTF8).
				contentType(MimeType.TEXT_HTML_UTF8).
				body(HttpUtil.htmlMessage("405 Method Not Allowed", ""));
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
			line = HttpUtil.readLine(in);
		} catch (EOFWhileReadingLineException e) {
			if (e.getLineSoFar().length() == 0)
				throw new NoResponseException(e);
			throw new EOFWhileReadingFirstLineException(e.getLineSoFar());
		}

		Matcher matcher = pattern.matcher(line);
		boolean find = matcher.find();

		if (!find) {
			throw new RuntimeException("Invalid server response: " + line);
		}
		version = matcher.group(1);
		statusCode = Integer.parseInt(matcher.group(2));
		statusMessage = matcher.group(4);

	}

	@Override
	public void read(InputStream in, boolean createBody) throws IOException,
	EndOfStreamException {
		parseStartLine(in);

		if (getStatusCode() == 100) {
			HttpUtil.readLine(in);
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

		if (isBodyEmpty()) {
			log.debug("empty body created");
			body = new EmptyBody();
			return;
		}

		super.createBody(in);
	}

	public boolean isRedirect() {
		return statusCode >= 300 && statusCode < 400;
	}

	public boolean hasNoContent() {
		return statusCode == 204;
	}

	@Override
	public String getName() {
		return " " + statusCode;
	}

	@Override
	public boolean isBodyEmpty() throws IOException {
		if (statusCode == 100 || statusCode == 101 || statusCode == 204 || statusCode == 205)
			return true;
		return super.isBodyEmpty();
	}

	public boolean isOk(){
		return statusCode >= 200 && statusCode < 300;
	}

	public boolean isUserError() {
		return statusCode >= 400 && statusCode < 500;
	}

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
