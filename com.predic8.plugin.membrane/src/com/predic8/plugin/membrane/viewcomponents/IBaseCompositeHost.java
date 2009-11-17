package com.predic8.plugin.membrane.viewcomponents;

import com.predic8.membrane.core.exchange.Exchange;

public interface IBaseCompositeHost {

	public Exchange getExchange();
	
	public void setRequestSaveEnabled(boolean status);
	
	public void setResponseSaveEnabled(boolean status);
	
	public void setRequestFormatEnabled(boolean status);
	
	public void setResponseFormatEnabled(boolean status);
	
}
