package com.predic8.membrane.core.interceptor;

import java.util.ArrayList;
import java.util.List;

import com.predic8.membrane.core.exchange.Exchange;

public class MockInterceptor extends AbstractInterceptor {

	private String label;
	
	public static List<String> reqLabels = new ArrayList<String>();
	
	public static List<String> respLabels = new ArrayList<String>();
	
	public MockInterceptor(String label) {
		this.label = label;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		reqLabels.add(label);
		return super.handleRequest(exc);
	}
	
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		respLabels.add(label);
		return super.handleResponse(exc);
	}
}
