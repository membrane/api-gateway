/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.tunnel;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URLUtil;

/**
 * @description Allow HTTP protocol upgrades to the <a
 *              href="http://tools.ietf.org/html/rfc6455">WebSocket protocol</a>.
 *              After the upgrade, the connection's data packets are simply forwarded
 *              and not inspected.
 * @default false
 */
@MCElement(name = "webSocket")
public class WebSocketInterceptor extends AbstractInterceptor {
	private String url;
	private String pathQuery;

	public String getUrl() {
		return url;
	}

	/**
	 * @description The URL the WebSocket connection will be forwarded to. The (host,port) pair specifies the target server.
	 * The (path,query) part are sent to the target server on the initial request. (For example, ActiveMQ listens on port
	 * 61614 and expects the incoming WebSocket connection to have a path '/' and empty query.)
	 * @example http://localhost:61614/
	 */
	@MCAttribute
	public void setUrl(String url) {
		this.url = url;
	}

	@Override
	public void init() throws Exception {
		pathQuery = url == null ? null : URLUtil.getPathQuery(getRouter().getUriFactory(), url);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if ("websocket".equalsIgnoreCase(exc.getRequest().getHeader().getFirstValue("Upgrade"))) {
			exc.setProperty(Exchange.ALLOW_WEBSOCKET, Boolean.TRUE);
			if (url != null) {
				exc.getRequest().setUri(pathQuery);
				exc.getDestinations().set(0, url);
			}
		}
		return Outcome.CONTINUE;
	}
}
