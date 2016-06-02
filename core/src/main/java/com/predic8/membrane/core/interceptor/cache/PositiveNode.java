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

import java.io.IOException;
import java.text.ParseException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.util.ByteUtil;

class PositiveNode extends Node {
	private static final long serialVersionUID = 1L;

	static final Log log = LogFactory.getLog(PositiveNode.class.getName());


	byte[] content;
	long lastModified;
	String contentType;
	String inResponseToAccept;
	String location;
	int status;

	public PositiveNode(Exchange exchange) throws IOException, ParseException {
		Request request = exchange.getRequest();
		Response response = exchange.getResponse();
		content = ByteUtil.getByteArrayData(response.getBodyAsStreamDecoded());
		contentType = response.getHeader().getFirstValue(Header.CONTENT_TYPE);
		lastModified = CacheInterceptor.fromRFC(response.getHeader().getFirstValue(Header.LAST_MODIFIED));
		inResponseToAccept = request.getHeader().getNormalizedValue(Header.ACCEPT);
		location = response.getHeader().getFirstValue(Header.LOCATION);
		status = response.getStatusCode();

		/*
		if (contentType == null) {
			System.err.println("strange case with contentType == null");
			System.err.println(request.getStartLine());
			System.err.println(request.getHeader());
			System.err.println(response.getStartLine());
			System.err.println(response.getHeader());
		}
		 */
	}

	@Override
	public Response toResponse(Request request) {
		String ifModifiedSince = request.getHeader().getFirstValue(Header.IF_MODIFIED_SINCE);
		if (ifModifiedSince != null) {
			try {
				if (lastModified <= CacheInterceptor.fromRFC(ifModifiedSince))
					return Response.notModified(CacheInterceptor.toRFC(System.currentTimeMillis())).build();
			} catch (Exception e) {
				log.warn("", e);
			}
		}
		ResponseBuilder builder = Response.ok();
		if (status >= 300 && status < 400)
			builder.status(status, "Moved.");
		if (contentType != null)
			builder.contentType(contentType);
		if (location != null)
			builder.header(Header.LOCATION, location);
		if (lastModified != 0)
			return builder.header(Header.LAST_MODIFIED, CacheInterceptor.toRFC(lastModified)).body(content).build();
		else
			return builder.body(content).build();
	}

	@Override
	public boolean canSatisfy(Request request) {
		String accept = request.getHeader().getFirstValue("accept");
		if (accept != null) {
			if (inResponseToAccept.equals(accept))
				return true;
			if (inResponseToAccept.startsWith(accept + ",") || inResponseToAccept.endsWith("," + accept) || inResponseToAccept.contains("," + accept + ","))
				return true;
			if (accept.endsWith("*")) {
				accept = accept.substring(0, accept.length() - 1);
				return contentType != null && contentType.startsWith(accept);
			}
			CacheInterceptor.log.warn("Cannot check cache node satisfaction: 'Accept: " + accept + "'");
			return false;
		}
		return true; // TODO
	}
}