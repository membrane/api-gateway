package com.predic8.membrane.core.interceptor.ratelimit;

import java.util.concurrent.atomic.AtomicInteger;

public class IPAddressInfo {
	public AtomicInteger currentRequests = new AtomicInteger(0);

}
