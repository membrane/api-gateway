package com.predic8.membrane.core.transport.http;

import java.util.concurrent.ThreadFactory;

public class HttpServerThreadFactory implements ThreadFactory {

	@Override
	public Thread newThread(Runnable r) {
		Thread th = new Thread(r);
		th.setName("RouterThread");
		return th;
	}

}
