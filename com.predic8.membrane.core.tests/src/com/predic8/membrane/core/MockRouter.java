package com.predic8.membrane.core;

import com.predic8.membrane.core.transport.Transport;
import com.predic8.membrane.core.transport.http.MockHttpTransport;

public class MockRouter extends Router {

	@Override
	public Transport getTransport() {
		return new MockHttpTransport();
	}
	
}
