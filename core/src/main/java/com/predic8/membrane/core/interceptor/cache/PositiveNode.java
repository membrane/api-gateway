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

import java.io.*;
import java.text.ParseException;

import com.predic8.membrane.core.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.util.ByteUtil;

import static com.predic8.membrane.core.http.Header.*;

class PositiveNode extends Node {
	@Serial
	private static final long serialVersionUID = 1L;

	static final Logger log = LoggerFactory.getLogger(PositiveNode.class.getName());


	byte[] content;
	long lastModified;
	String contentType;
	String inResponseToAccept;
	String location;
	int status;

	public PositiveNode(Exchange exchange) throws IOException, ParseException {
		Response response = exchange.getResponse();
		content = ByteUtil.getByteArrayData(response.getBodyAsStreamDecoded());
		contentType = response.getHeader().getFirstValue(CONTENT_TYPE);
		lastModified = CacheInterceptor.fromRFC(response.getHeader().getFirstValue(LAST_MODIFIED));
		inResponseToAccept = exchange.getRequest().getHeader().getNormalizedValue(ACCEPT);
		location = response.getHeader().getFirstValue(LOCATION);
		status = response.getStatusCode();
	}

	@Override
	public Response toResponse(Request request) {
		String ifModifiedSince = request.getHeader().getFirstValue(IF_MODIFIED_SINCE);
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
			builder.header(LOCATION, location);
		if (lastModified != 0)
			return builder.header(LAST_MODIFIED, CacheInterceptor.toRFC(lastModified)).body(content).build();
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