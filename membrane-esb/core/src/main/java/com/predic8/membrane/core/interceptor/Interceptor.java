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

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.XMLElement;
import com.predic8.membrane.core.exchange.Exchange;

public interface Interceptor extends XMLElement {

	public enum Flow {
		REQUEST_RESPONSE, REQUEST, RESPONSE;
	}
	
	public Outcome handleRequest(Exchange exc) throws Exception;
	
	public Outcome handleResponse(Exchange exc) throws Exception;
	
	public String getDisplayName();
	
	public void setDisplayName(String name);
	
	public String getId();
	
	public void setId(String id);
	
	public void setRouter(Router router);
	
	public Router getRouter();

	public void setFlow(Flow flow);	
	
	public Flow getFlow();
	
	public String getShortDescription();
	public String getLongDescription();
	
	/**
	 * @return "access-control" if http://membrane-soa.org/esb-doc/current/configuration/reference/access-control.htm is the documentation page
	 * for this interceptor, or null if there is no such page.
	 */
	public String getHelpId();
	
}
