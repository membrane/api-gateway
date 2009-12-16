package com.predic8.membrane.core.exchange.accessors;

import java.text.SimpleDateFormat;

import com.predic8.membrane.core.exchange.Exchange;

public class TimeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Time";
	
	private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");
	
	public String get(Exchange exc) {
		if (exc == null)
			return "";
		return exc.getTime().toString();
	}

	public String getId() {
		return ID;
	}

}
