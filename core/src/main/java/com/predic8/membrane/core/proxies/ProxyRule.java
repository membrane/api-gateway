/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.annot.*;

/**
 * @description
 * Clients can send HTTP requests to a proxy that forward the request to a Web server. It acts on behalf of the client.
 * @topic 2. Proxies
 */
@MCElement(name="proxy")
public class ProxyRule extends SSLableProxy {

	public static final String ELEMENT_NAME = "proxy";

	public ProxyRule() {
		key = new ProxyRuleKey(80);
	}

	public ProxyRule(ProxyRuleKey ruleKey) {
		super(ruleKey);
	}

}
