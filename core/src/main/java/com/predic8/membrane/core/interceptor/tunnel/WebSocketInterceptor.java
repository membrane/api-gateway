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
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.predic8.membrane.core.exchange.Exchange.ALLOW_WEBSOCKET;
import static com.predic8.membrane.core.http.Header.SEC_WEBSOCKET_EXTENSIONS;
import static java.lang.Boolean.TRUE;

/**
 * @description Lets clients upgrade a connection to the WebSocket protocol (RFC 6455) and relays it between client
 * and backend. Without this plugin, upgrade requests are rejected. The upgrade request passes the flow like any
 * other request, so plugins before <code>webSocket</code>, e.g. for authentication, apply to it.
 * <p>
 * Without frame interceptors, the bytes of the connection are relayed unchanged. With frame interceptors, every
 * message passes them as one complete frame: fragmented messages are joined, and no WebSocket extension such as
 * permessage-deflate is negotiated. If a frame interceptor fails, both peers are sent a close frame with status
 * 1011 (internal error).
 * </p>
 * <pre>
 * webSocket:
 *   [ url: &lt;url&gt; ]
 *   [ maxMessageSize: &lt;bytes&gt; ]
 *   [ flow:
 *       - wsLog: {} | wsStompReassembler: ... | wsInterceptor: ...
 *       ... ]
 * </pre>
 * @topic 7. Transports and Clients
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - webSocket:
 *         flow:
 *           - wsLog: {}
 *   target:
 *     host: localhost
 *     port: 8080
 * </code></pre>
 */
@MCElement(name = "webSocket")
public class WebSocketInterceptor extends AbstractInterceptor {

	protected static final Logger log = LoggerFactory.getLogger(WebSocketInterceptor.class);

	private String url;
	private String pathQuery;
	private int maxMessageSize = WebSocketFrameAssembler.DEFAULT_MAX_PAYLOAD_SIZE;
	private List<WebSocketInterceptorInterface> interceptors = new ArrayList<>();

	@Override
	public void init() {
		super.init();
		if (maxMessageSize < 1 || maxMessageSize > WebSocketFrameAssembler.MAX_SUPPORTED_PAYLOAD_SIZE)
			throw new ConfigurationException("webSocket: maxMessageSize must be between 1 and %d, but is %d."
					.formatted(WebSocketFrameAssembler.MAX_SUPPORTED_PAYLOAD_SIZE, maxMessageSize));
        try {
            pathQuery = url == null ? null : URLUtil.getPathQuery(getRouter().getConfiguration().getUriFactory(), url);
        } catch (URISyntaxException e) {
            throw new ConfigurationException("Could not parse " + url,e);
        }
		for (WebSocketInterceptorInterface interceptor : interceptors) {
			try {
				interceptor.init(getRouter());
			} catch (Exception e) {
				throw new ConfigurationException("webSocket: could not initialize %s.".formatted(interceptor.getClass().getSimpleName()), e);
			}
		}
    }

	/**
	 * @return the <code>webSocket</code> plugin in the top level of the proxy's flow, if any
	 */
	public static Optional<WebSocketInterceptor> findIn(Proxy proxy) {
		return proxy.getFlow().stream()
				.filter(WebSocketInterceptor.class::isInstance)
				.map(WebSocketInterceptor.class::cast)
				.findFirst();
	}

	/**
	 * @return whether WebSocket frames on the proxy's connections have to be parsed, i.e. a <code>webSocket</code>
	 * plugin with a non-empty frame flow is configured. Otherwise the connection's bytes are relayed as they are.
	 */
	public static boolean hasFrameInterceptors(Proxy proxy) {
		return findIn(proxy).map(webSocket -> !webSocket.getFlow().isEmpty()).orElse(false);
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
			// frame interceptors need uncompressed messages they may refragment (RFC 6455 5.4)
			if (!interceptors.isEmpty())
				exc.getRequest().getHeader().removeFields(SEC_WEBSOCKET_EXTENSIONS);
		}
		return Outcome.CONTINUE;
	}

	@Override
	public String getShortDescription() {
		return "Allow HTTP protocol upgrades to the <a href=\"http://tools.ietf.org/html/rfc6455\">WebSocket protocol</a>. After the upgrade, the connection is relayed between client and backend.";
	}

	public String getUrl() {
		return url;
	}

	/**
     * @description Backend for WebSocket connections, instead of the API's target. Its host and port select the
     * server, its path and query replace those of the upgrade request. Other requests still go to the target.
     * @example http://localhost:61614/
     */
	@MCAttribute
	public void setUrl(String url) {
		this.url = url;
	}

	public int getMaxMessageSize() {
		return maxMessageSize;
	}

	/**
	 * @description Maximum size of a message in bytes, in either direction, when frame interceptors are configured.
	 * Each message is held in memory while it passes them. A peer that sends a larger message is sent a close frame
	 * with status 1009 (message too big), and the connection is closed. Without frame interceptors, there is no limit.
	 * @default 16777216
	 * @example 1048576
	 */
	@MCAttribute
	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	public List<WebSocketInterceptorInterface> getFlow() {
		return interceptors;
	}

	/**
	 * @description Frame interceptors. Messages from the client pass them in the given order, messages from the
	 * server in reverse order.
	 */
	@MCChildElement
	public void setFlow(List<WebSocketInterceptorInterface> flow) {
		this.interceptors = flow;
	}
}
