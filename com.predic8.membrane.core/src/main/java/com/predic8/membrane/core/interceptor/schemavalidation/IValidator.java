package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Outcome;

public interface IValidator {
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception;
	
	public long getValid();
	
	public long getInvalid();

}
