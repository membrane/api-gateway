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
package com.predic8.membrane.core.interceptor.rewrite;

import java.util.Map;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class SimpleURLRewriteInterceptor extends AbstractInterceptor {

	private Map<String, String> mapping;

	public SimpleURLRewriteInterceptor() {
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = mapping.get(exc.getRequest().getUri());
		
		if (uri == null)
			return Outcome.CONTINUE;
		
		exc.getRequest().setUri(uri);
		return Outcome.CONTINUE;
	}
	
	public void setMapping(Map<String, String> mapping) {
		this.mapping = mapping;
	}
	

}
