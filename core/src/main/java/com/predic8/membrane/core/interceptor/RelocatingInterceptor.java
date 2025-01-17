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

import com.googlecode.jatl.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.ws.relocator.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

abstract public class RelocatingInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RelocatingInterceptor.class.getName());

	protected String host;
	protected String protocol;
	protected String port;
	protected Relocator.PathRewriter pathRewriter;

	@Override
	public Outcome handleResponse(Exchange exc) {

		if (exc.getProxy() instanceof ProxyRule) {
			log.debug("{} ProxyRule found: No relocating done!",name);
			return CONTINUE;
		}

		if (!wasGetRequest(exc)) {
			log.debug("{} HTTP method wasn't GET: No relocating done!",name);
			return CONTINUE;
		}

		if (!hasContent(exc)) {
			log.debug("{} No Content: No relocating done!",name);
			return CONTINUE;
		}

		if (!exc.getResponse().isXML()) {
			log.debug("{} Body contains no XML: No relocating done!",name);
			return CONTINUE;
		}

		try {
			rewrite(exc);
		} catch (Exception e) {
			log.error("",e);
			internal(router.isProduction(),getDisplayName())
					.detail("Error rewriting URI")
					.topLevel("URI", exc.getRequestURI())
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
		}
		return CONTINUE;
	}

	abstract void rewrite(Exchange exc) throws Exception;

	private boolean hasContent(Exchange exc) {
		return exc.getResponse().getHeader().getContentType() != null;
	}

	private boolean wasGetRequest(Exchange exc) {
		return Request.METHOD_GET.equals(exc.getRequest().getMethod());
	}

	protected int getLocationPort(Exchange exc) {
		if ("".equals(port)) {
			return -1;
		}
		if (port != null)
			return Integer.parseInt(port);
		return exc.getHandler().getLocalPort();
	}

	protected String getLocationHost(Exchange exc) {
		if (host != null)
			return host;

		String locHost = exc.getOriginalHostHeaderHost();

		log.debug("host {}",locHost);

		if (locHost == null) {
			return "localhost";
		}

		return locHost;
	}

	protected String getLocationProtocol() {
		if (protocol != null)
			return protocol;
		return "http";
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public void setPathRewriter(Relocator.PathRewriter pathRewriter) {
		this.pathRewriter = pathRewriter;
	}

	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(getShortDescription());
		sb.append("<br/>");
		sb.append("The protocol, host and port of the incoming request will be used for the substitution");

		if (protocol != null || port != null || host != null) {
			sb.append(" except the following fixed values:");
			StringWriter sw = new StringWriter();
			new Html(sw){{
				table();
				thead();
				tr();
				th().text("Part").end();
				th().text("Value").end();
				end();
				end();
				tbody();
				if (protocol != null) {
					tr();
					td().text("Protocol").end();
					td().text(protocol).end();
					end();
				}
				if (host != null) {
					tr();
					td().text("Host").end();
					td().text(host).end();
					end();
				}
				if (port != null) {
					tr();
					td().text("Port").end();
					td().text(port).end();
					end();
				}
				end();
				end();
			}};
			sb.append(sw);
		} else {
			sb.append(".");
		}
		return sb.toString();
	}
}
