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

import java.util.*;

import javax.xml.stream.*;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.config.security.Security;

public class Global extends AbstractConfigElement {

	public Map<String, Object> values = new HashMap<String, Object>();

	private ProxyConfiguration proxyConfiguration;

	private Security security;

	public Global(Router router) {
		super(router);
		security = new Security(router);
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {

		if ("router".equals(child)) {
			EmptyComplexElement r = new EmptyComplexElement();
			r.parse(token);
			values.put(Proxies.ADJ_HOST_HEADER,
					r.getAttribute("adjustHostHeader"));
		} else if ("monitor-gui".equals(child)) {
			EmptyComplexElement r = new EmptyComplexElement();
			r.parse(token);
			values.put(Proxies.TRACK_EXCHANGE, r.getAttribute("autoTrack"));
			values.put(Proxies.INDENT_MSG, r.getAttribute("indentMessage"));
		} else if ("proxyConfiguration".equals(child)) {
			proxyConfiguration = (ProxyConfiguration) new ProxyConfiguration(
					router).parse(token);
		} else if ("security".equals(child)) {
			security.parse(token);
		}
	}

	public Map<String, Object> getValues() {
		return values;
	}

	public void setValues(Map<String, Object> values) {
		this.values = values;
	}

	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("global");

		out.writeStartElement("router");
		out.writeAttribute("adjustHostHeader",
				"" + values.get(Proxies.ADJ_HOST_HEADER));
		out.writeEndElement();
		out.writeStartElement("monitor-gui");
		out.writeAttribute("indentMessage", "" + values.get(Proxies.INDENT_MSG));
		out.writeAttribute("autoTrack", "" + values.get(Proxies.TRACK_EXCHANGE));
		out.writeEndElement();

		if (proxyConfiguration != null) {
			proxyConfiguration.write(out);
		}

		if (security.isSet()) {
			security.write(out);
		}

		out.writeEndElement();
	}

	public ProxyConfiguration getProxyConfiguration() {
		return proxyConfiguration;
	}

	public void setProxyConfiguration(ProxyConfiguration proxyConfiguration) {
		this.proxyConfiguration = proxyConfiguration;
	}

	public Security getSecurity() {
		return security;
	}

	public void setSecurity(Security security) {
		this.security = security;
	}

}
