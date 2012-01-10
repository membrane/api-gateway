/* Copyright 2009 predic8 GmbH, www.predic8.com

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
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.HttpUtil;

public class Response extends Message {

	protected static Log log = LogFactory.getLog(Response.class.getName());

	private int statusCode;

	private String statusMessage;

	private static Pattern pattern = Pattern.compile("HTTP/(.+?) (.+?) (.+?)$");

	public static class ResponseBuilder {
		private Response res = new Response();

		public Response build() {
			return res;
		}

		public ResponseBuilder status(int code, String msg) {
			res.setStatusCode(code);
			res.setStatusMessage(msg);
			return this;
		}

		public ResponseBuilder entity(String msg) {
			try {
				res.setBodyContent(msg.getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
			return this;
		}
		
		public ResponseBuilder body(AbstractBody body) {
			res.setBody(body);
			return this;
		}
		
		public ResponseBuilder body(byte[] body) {
			res.setBodyContent(body);
			return this;
		}

		public ResponseBuilder body(InputStream is) throws IOException {
			res.setBody(new Body(is, -1));
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

	}

	public static ResponseBuilder ok(String msg) throws Exception {
		return ok().contentType("text/html;charset=utf-8").entity(msg);
	}
	
	private static String SERVER_HEADER = "Membrane " + Constants.VERSION + ". See http://membrane-soa.org";
	
	public static ResponseBuilder ok() {
		return ResponseBuilder.newInstance().
							   status(200, "Ok").
							   header("Server", SERVER_HEADER);
	}
	
	public static ResponseBuilder noContent() {
		return ResponseBuilder.newInstance().
				status(204, "No Content");
	}
	
	
	public static ResponseBuilder badRequest() {
		return ResponseBuilder.newInstance().
				status(400, "Bad Request").
				header("Server", SERVER_HEADER);
	}

	public static ResponseBuilder badRequest(String message) {
		return ResponseBuilder.newInstance().
				status(400, "Bad Request").
				header("Server", SERVER_HEADER).
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Service Unavailable", message));
	}

	public static ResponseBuilder continue100() {
		return ResponseBuilder.newInstance().
				status(100, "Continue");
	}
	
	private static String htmlMessage(String caption, String text) {
		return "<html><head><title>" + StringEscapeUtils.escapeHtml(caption)
				+ "</title></head>" + "<body><h1>"
				+ StringEscapeUtils.escapeHtml(caption) + "</h1><p>"
				+ StringEscapeUtils.escapeHtml(text) + "</p></body></html>";
	}
	
	public static ResponseBuilder serverUnavailable(String message) {
		return ResponseBuilder.newInstance().
				status(503, "Service Unavailable").
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Service Unavailable", message));
	}
	
	public static ResponseBuilder interalServerError() {
		return ResponseBuilder.newInstance().
				status(500, "Internal Server Error").
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Internal Server Error", ""));
	}

	public static ResponseBuilder interalServerError(String message) {
		return ResponseBuilder.newInstance().
				status(500, "Internal Server Error").
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Internal Server Error", message));
	}
	
	public static ResponseBuilder forbidden() {
		return ResponseBuilder.newInstance().
				status(403, "Forbidden").
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Forbidden", ""));
	}

	public static ResponseBuilder forbidden(String message) {
		return ResponseBuilder.newInstance().
				status(403, "Forbidden").
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Forbidden", message));
	}

	public static ResponseBuilder unauthorized(String message) {
		return ResponseBuilder.newInstance().
				status(401, "Unauthorized.").
				contentType("text/html;charset=utf-8").
				entity(htmlMessage("Unauthorized.", message));
	}

	@Override
	public String getStartLine() {
		StringBuffer buf = new StringBuffer();
		buf.append("HTTP/");
		buf.append(version);
		buf.append(" ");
		buf.append(statusCode);
		buf.append(" ");
		buf.append(statusMessage);
		buf.append(Constants.CRLF);
		return buf.toString();
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

	public void parseStartLine(InputStream in) throws IOException,
			EndOfStreamException {

		Matcher matcher = pattern.matcher(HttpUtil.readLine(in));
		boolean find = matcher.find();

		if (!find) {
			return;
		}
		version = matcher.group(1);
		statusCode = Integer.parseInt(matcher.group(2));
		statusMessage = matcher.group(3);

	}

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
		if (statusCode == 100 || statusCode == 204 || statusCode == 205)
			return true;
		return super.isBodyEmpty();
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
		if (header.getContentType() != null)
			return false;
		return true;
	}
	
	@Override
	public boolean isKeepAlive() {
		if (isRedirect() && mayHaveNoBody())
			return false;
		return super.isKeepAlive();
	}

}
