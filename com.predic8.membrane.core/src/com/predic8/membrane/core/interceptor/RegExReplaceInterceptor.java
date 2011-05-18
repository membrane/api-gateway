/* Copyright 2011 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;

public class RegExReplaceInterceptor extends AbstractInterceptor {

	private String pattern;
	
	private String replacement;
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
	
		Response res = exc.getResponse();
		
		if (res.hasNoContent())
			return Outcome.CONTINUE;
		
		res.readBody();
		byte[] content = res.getBody().getContent();
		res.setBodyContent(new String(content).replaceAll(pattern, replacement).getBytes());
		
		return Outcome.CONTINUE;
	}

	public String getPattern() {
		return pattern;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public String getReplacement() {
		return replacement;
	}

	public void setReplacement(String replacement) {
		this.replacement = replacement;
	}
	
}
