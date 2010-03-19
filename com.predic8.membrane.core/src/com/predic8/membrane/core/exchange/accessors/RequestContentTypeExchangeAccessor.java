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

package com.predic8.membrane.core.exchange.accessors;

import com.predic8.membrane.core.exchange.Exchange;

public class RequestContentTypeExchangeAccessor implements ExchangeAccessor {

	public static final String ID = "Request Content-Type";
	
	public Object get(Exchange exc) {
		if (exc == null || exc.getRequest() == null || exc.getRequest().getHeader().getContentType() == null)
			return "";
		return getContentType(exc);
	}

	public String getId() {
		return ID;
	}
	
	private String getContentType(Exchange exc) {
		String contentType = (String) exc.getRequest().getHeader().getContentType();
		
		int index = contentType.indexOf(";");
		if (index > 0) {
			contentType = contentType.substring(0, index);
		}
		return contentType;
	}

}
