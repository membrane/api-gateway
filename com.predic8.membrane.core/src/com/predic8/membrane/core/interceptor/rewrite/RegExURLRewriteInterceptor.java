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
package com.predic8.membrane.core.interceptor.rewrite;

import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class RegExURLRewriteInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(RegExURLRewriteInterceptor.class.getName());
	
	private Map<String, String> mapping;

	public RegExURLRewriteInterceptor() {
		priority = 150;
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		String uri = exc.getRequest().getUri();
		
		log.debug("uri: "+uri);

		String regex = findFirstMatchingRegEx(uri);
		if (regex == null ) return Outcome.CONTINUE;
		
		log.debug("match found: "+regex);
		log.debug("replacing with: "+mapping.get(regex));		
		
		exc.getRequest().setUri(replace(uri, regex));
		return Outcome.CONTINUE;
	}
	
	private String replace(String uri, String regex) {		
		String replaced = uri.replaceAll(regex, mapping.get(regex));
		
		log.debug("replaced URI: "+replaced);
		
		return replaced;
	}

	private String findFirstMatchingRegEx(String uri) {
		for (String regex : mapping.keySet()) {
			if ( Pattern.matches(regex, uri) ) return regex;
		}
		return null;
	}

	public void setMapping(Map<String, String> mapping) {
		this.mapping = mapping;
	}
	

}
