/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.http;

import java.util.concurrent.ThreadFactory;

public class HttpServerThreadFactory implements ThreadFactory {

	public static final String DEFAULT_THREAD_NAME = "RouterThread"; 
	
	public final String defaultThreadName; 
	
	public HttpServerThreadFactory() {
		defaultThreadName = DEFAULT_THREAD_NAME;
	}

	public HttpServerThreadFactory(String threadName) {
		defaultThreadName = threadName;
	}
	
	@Override
	public Thread newThread(Runnable r) {
		Thread th = new Thread(r);
		th.setName(defaultThreadName);
		return th;
	}

}
