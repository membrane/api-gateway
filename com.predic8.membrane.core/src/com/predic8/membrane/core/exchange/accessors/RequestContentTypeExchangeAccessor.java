package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class RequestContentTypeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Request Content-Type";
	
	public String get(Exchange exc) {
		if (exc == null || exc.getRequest() == null || exc.getRequest().getHeader().getContentType() == null)
			return "";
		return getContentType(exc);
	}

	public String getId() {
		return ID;
	}
	
	private String getContentType(Exchange exc) {
		String contentType = (String) exc.getRequest().getHeader().getContentType();
		
		int index = contentType.indexOf(";");
		if (index > 0) {
			contentType = contentType.substring(0, index);
		}
		return contentType;
	}

}
