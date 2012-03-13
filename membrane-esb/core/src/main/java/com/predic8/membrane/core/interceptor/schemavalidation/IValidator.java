package com.predic8.membrane.core.interceptor.schemavalidation;

import java.io.InputStream;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Outcome;

public interface IValidator {
	/**
	 * Remove after dependent projects have been updated.
	 * @deprecated Use {@link #validateMessage(Exchange, InputStream)} instead. 
	 */
	public Outcome validateMessage(Exchange exc, Message msg) throws Exception;
	
	public Outcome validateMessage(Exchange exc, InputStream body) throws Exception;
	
	public long getValid();
	
	public long getInvalid();

}
