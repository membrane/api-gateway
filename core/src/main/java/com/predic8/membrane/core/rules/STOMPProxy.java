/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core.rules;

import com.predic8.membrane.annot.MCElement;

/**
 * @description <p>Proxies incoming STOMP CONNECT requests. Use a &lt;stompClient&gt; to forward these requests so some other machine.</p>
 * @topic 2. Proxies
 */
@MCElement(name="stompProxy")
public class STOMPProxy extends SSLableProxy {

	public STOMPProxy() {
		this.key = new ServiceProxyKey(80);
		((ServiceProxyKey)key).setMethod("CONNECT");
	}

	@Override
	protected AbstractProxy getNewInstance() {
		return new STOMPProxy();
	}

}
