package com.predic8.membrane.core.util;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;

public class TestUtil {

	public static Request getGetRequest(String uri) {
		Request req = getStandartRequest(Request.METHOD_GET);
		req.setUri(uri);
		return req;
	}
	
	public static Request getPostRequest(String uri) {
		Request req = getStandartRequest(Request.METHOD_POST);
		req.setUri(uri);
		return req;
	}
	
	private static Request getStandartRequest(String method) {
		Request request = new Request();
		request.setMethod(method);
		request.setVersion(Constants.HTTP_VERSION_11);
		
		return request;
	}
	
	public static Response getOKResponse(byte[] content, String contentType) {
		Response res = new Response();
		res.setBodyContent(content);
		res.setVersion("1.1");
		res.setStatusCode(200);
		res.setStatusMessage("OK");
		res.getHeader().setContentType(contentType);
		
		return res;
	}

}
