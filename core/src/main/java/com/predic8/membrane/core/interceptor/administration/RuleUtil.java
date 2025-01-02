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

package com.predic8.membrane.core.interceptor.administration;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.proxies.*;

public class RuleUtil {
	public static String getRuleIdentifier(Proxy proxy) {
		return proxy.toString() + (proxy.getKey().getPort() == -1 ? "" : ":" + proxy.getKey().getPort());
	}

	public static Proxy findRuleByIdentifier(Router router, String name) {
		for (Proxy proxy : router.getRuleManager().getRules()) {
			if ( name.equals(getRuleIdentifier(proxy))) return proxy;
		}
		return null;
	}
}
