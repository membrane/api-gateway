package com.predic8.membrane.core.interceptor.cache;

import java.io.Serializable;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;

abstract class Node implements Serializable {
	private static final long serialVersionUID = 1L;

	public boolean canSatisfy(Request request) {
		return true; // TODO
	}
	
	public abstract Response toResponse(Request request);
}