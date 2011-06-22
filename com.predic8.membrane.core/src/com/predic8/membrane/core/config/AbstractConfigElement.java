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

package com.predic8.membrane.core.config;


import com.predic8.membrane.core.Router;

public abstract class AbstractConfigElement extends AbstractXmlElement {

	/**
	 * Needed to resolve interceptor IDs into interceptor beans
	 */	
	protected Router router;
	 
	public AbstractConfigElement(Router router) {
		this.router = router;
	}
	
	public void setRouter(Router router) {
		this.router = router;
	}
	
	public Router getRouter() { //wird von ReadRulesConfigurationTest aufgerufen.		
		return router;
	}		
}
