/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.services;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class DummyWebServiceInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(DummyWebServiceInterceptor.class.getName());

	public volatile long counter;
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		exc.setResponse(Response.ok().contentType("text/html").body("<aaa></aaa>".getBytes()).build());
		counter ++;
		log.debug("handle request "+counter);
		return Outcome.RETURN;
	}
	
}
