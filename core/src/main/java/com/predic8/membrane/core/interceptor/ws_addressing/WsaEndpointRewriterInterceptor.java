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
package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name="wsaEndpointRewriter")
public class WsaEndpointRewriterInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(WsaEndpointRewriterInterceptor.class);

	private String protocol;
	private String host;

	// -1 = do not change port
	private int port = -1;

	@Override
	public Outcome handleResponse(Exchange exc) {
		return handleInternal(exc, RESPONSE);
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		return handleInternal(exc, REQUEST);
	}

	private @NotNull Outcome handleInternal(Exchange exchange, Flow flow) {
		Message message = exchange.getMessage(flow);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try {
			// Why is port 2020 hard coded?
            new WsaEndpointRewriter().rewriteEndpoint(message.getBodyAsStreamDecoded(), output, new Location( protocol, host,  port));
        } catch (Exception e) {
			log.error("",e);
			internal(router.isProduction(),getDisplayName())
					.detail("Could not rewrite endpoint!")
					.exception(e)
					.buildAndSetResponse(exchange);
			return ABORT;
        }
		message.setBodyContent(output.toByteArray());
		return CONTINUE;
	}

	public String getProtocol() {
		return protocol;
	}

	@MCAttribute
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getHost() {
		return host;
	}

	@MCAttribute
	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	/**
	 * -1 = do not change port
	 * @param port
	 */
	@MCAttribute
	public void setPort(int port) {
		this.port = port;
	}

	public record Location(String protocol, String host, int port) {}
}