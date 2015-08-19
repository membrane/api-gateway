package com.predic8.membrane.core.interceptor.cache;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;

class NegativeNode extends Node {
	private static final long serialVersionUID = 1L;

	@Override
	public Response toResponse(Request request) {
		return Response.notFound().build();
	}

}