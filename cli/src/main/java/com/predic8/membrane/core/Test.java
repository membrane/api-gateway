package com.predic8.membrane.core;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.util.URIFactory;

public class Test {
	public static void main(String[] args) throws Exception {
		
		URIFactory uriFactory = new URIFactory();
		uriFactory.setAllowIllegalCharacters(true);
		Response res = new HttpClient().call(new Request.Builder().get(uriFactory, "http://localhost:2000/a.{/").buildExchange()).getResponse();
		System.out.println(res.getStatusCode());
		System.out.println(res.getStartLine());
	}
}
