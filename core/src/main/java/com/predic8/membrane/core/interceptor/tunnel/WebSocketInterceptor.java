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
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static java.lang.Boolean.TRUE;

/**
 * @description Allow HTTP protocol upgrades to the <a
 *              href="http://tools.ietf.org/html/rfc6455">WebSocket protocol</a>.
 *              After the upgrade, the connection's data packets are simply forwarded
 *              and not inspected.
 * @default false
 */
@MCElement(name = "webSocket")
public class WebSocketInterceptor extends AbstractInterceptor {

	protected static final Logger log = LoggerFactory.getLogger(WebSocketInterceptor.class);

	private String url;
	private String pathQuery;
	private int maxFramePayloadSize = WebSocketFrameAssembler.DEFAULT_MAX_PAYLOAD_SIZE;
	private List<WebSocketInterceptorInterface> interceptors = new ArrayList<>();

	@Override
	public void init() {
		super.init();
		if (maxFramePayloadSize < 1 || maxFramePayloadSize > WebSocketFrameAssembler.MAX_SUPPORTED_PAYLOAD_SIZE)
			throw new ConfigurationException("webSocket: maxFramePayloadSize must be between 1 and %d, but is %d."
					.formatted(WebSocketFrameAssembler.MAX_SUPPORTED_PAYLOAD_SIZE, maxFramePayloadSize));
        try {
            pathQuery = url == null ? null : URLUtil.getPathQuery(getRouter().getConfiguration().getUriFactory(), url);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("Could not parse " + url,e);
        }
    }

	@Override
	public String getDisplayName() {
		return "websocket";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		if ("websocket".equalsIgnoreCase(exc.getRequest().getHeader().getFirstValue("Upgrade"))) {
			exc.setProperty(ALLOW_WEBSOCKET, TRUE);
			if (url != null) {
				exc.getRequest().setUri(pathQuery);
				exc.getDestinations().set(0, url);
			}
		}
		return Outcome.CONTINUE;
	}

	@Override
	public String getShortDescription() {
		return "Allow HTTP protocol upgrades to the <a href=\"http://tools.ietf.org/html/rfc6455\">WebSocket protocol</a>. After the upgrade, the connection's data packets are simply forwarded and not inspected.";
	}

	public String getUrl() {
		return url;
	}

	/**
     * @description The URL the WebSocket connection will be forwarded to. The (host,port) pair specifies the target server.
     * The (path,query) part are sent to the target server on the initial request. (For example, ActiveMQ listens on port
     * 61614 and expects the incoming WebSocket connection to have a path '/' and empty query.)
     * @example <a href="http://localhost:61614/">http://localhost:61614/</a>
     */
	@MCAttribute
	public void setUrl(String url) {
		this.url = url;
	}

	public int getMaxFramePayloadSize() {
		return maxFramePayloadSize;
	}

	/**
	 * @description Maximum payload size of a single WebSocket frame in bytes, in either direction. Every frame
	 * is held in memory while it passes the gateway. When a peer announces a larger frame, the connection is
	 * closed before the payload is read, and that peer is sent a close frame with status 1009 (message too big).
	 * @default 16777216
	 * @example 1048576
	 */
	@MCAttribute
	public void setMaxFramePayloadSize(int maxFramePayloadSize) {
		this.maxFramePayloadSize = maxFramePayloadSize;
	}

	public List<WebSocketInterceptorInterface> getFlow() {
		return interceptors;
	}

	@MCChildElement
	public void setFlow(List<WebSocketInterceptorInterface> flow) {
		this.interceptors = flow;
	}
}
