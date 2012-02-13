package com.predic8.membrane.core.http;

import com.predic8.membrane.core.Constants;



public class OKResponse extends Response {

	public OKResponse() {
		setStatusCode(200);
		setStatusMessage("OK");
		getHeader().add("Server", "Membrane " + Constants.VERSION + ". See http://membrane-soa.org");
	}
	
}
