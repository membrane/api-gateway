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

import com.googlecode.jatl.Html;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.ws.relocator.Relocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.*;

import static com.predic8.membrane.core.Constants.WADL_NS;

@MCElement(name="wadlRewriter")
public class WADLInterceptor extends RelocatingInterceptor {

	private static Logger log = LoggerFactory.getLogger(WADLInterceptor.class.getName());

	public WADLInterceptor() {
		name = "WADL Rewriting Interceptor";
		setFlow(Flow.Set.RESPONSE_ABORT);
	}

	@Override
	public String getShortDescription() {
		return "Rewrites REST endpoint addresses and XML Schema locations in WADL and XSD documents.";
	}

	@Override
	protected void rewrite(Exchange exc) throws Exception, IOException {

		log.debug("Changing endpoint address in WADL");

		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		Relocator relocator = new Relocator(new OutputStreamWriter(stream,
				exc.getResponse().getCharset()), getLocationProtocol(), getLocationHost(exc),
				getLocationPort(exc), exc.getHandler().getContextPath(exc), pathRewriter);

		relocator.getRelocatingAttributes().put(
				new QName(WADL_NS, "resources"), "base");
		relocator.getRelocatingAttributes().put(new QName(WADL_NS, "include"),
				"href");

		relocator.relocate(new InputStreamReader(exc.getResponse().getBodyAsStreamDecoded(), exc.getResponse().getCharset()));

		exc.getResponse().setBodyContent(stream.toByteArray());
	}

	@MCAttribute
	@Override
	public void setProtocol(String protocol) {
		super.setProtocol(protocol);
	}

	@MCAttribute
	@Override
	public void setHost(String host) {
		super.setHost(host);
	}

	@MCAttribute
	@Override
	public void setPort(String port) {
		super.setPort(port);
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
			sb.append(sw.toString());
		} else {
			sb.append(".");
		}
		return sb.toString();
	}
}
