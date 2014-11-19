package com.predic8.membrane.core.model;

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;

public abstract class AbstractExchangeViewerListener implements IExchangeViewerListener {

	@Override
	public void addRequest(Request request) {
	}

	@Override
	public void addResponse(Response response) {
	}

	@Override
	public void removeExchange() {
	}

	@Override
	public void setExchangeFinished() {
	}

	@Override
	public void setExchangeStopped() {
	}

}
