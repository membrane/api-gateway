/* Copyright 2009 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor;

import java.io.IOException;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.Message;

public class LogInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(LogInterceptor.class.getName());
	
	public LogInterceptor() {
		name = "Log";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.info("==== Request ===");
		logMessage(exc.getRequest());
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		log.info("==== Response ===");
		logMessage(exc.getResponse());
		return Outcome.CONTINUE;
	}
	
	private void logMessage(Message msg) throws IOException {
		log.info(msg==null?"N/A":msg);		
		log.info("================");
	}
}

