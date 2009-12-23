package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class TimeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Time";
	
//	private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd hh:mm:ss");
	
	public Object get(Exchange exc) {
		if (exc == null)
			return 0;
		return exc.getTime().getTimeInMillis();
	}

	public String getId() {
		return ID;
	}

}
