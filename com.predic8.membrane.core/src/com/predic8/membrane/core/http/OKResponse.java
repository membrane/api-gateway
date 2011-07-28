package com.predic8.membrane.core.http;



public class OKResponse extends Response {

	public OKResponse() {
		setStatusCode(200);
		setStatusMessage("OK");
		
		//getHeader().addHeader("Server", "Membrane" + Constants.VERSION);
	}
}
