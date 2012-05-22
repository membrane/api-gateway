/* Copyright 2010, 2012 predic8 GmbH, www.predic8.com

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

import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.flow.FlowController;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.rewrite.RewriteInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;

public class AdminConsoleInterceptor extends AbstractInterceptor {

	private final RewriteInterceptor r = new RewriteInterceptor();
	private final DynamicAdminPageInterceptor dapi = new DynamicAdminPageInterceptor();
	private final WebServerInterceptor wsi = new WebServerInterceptor();

	// these are the interceptors this interceptor consists of
	private final List<Interceptor> interceptors = Arrays.asList(new Interceptor[] { r, dapi, wsi });
	private final FlowController flowController = new FlowController();
	
	public AdminConsoleInterceptor() {
		name = "Administration";

		r.getMappings().add(new RewriteInterceptor.Mapping("^/?$", "/admin", "redirect"));
		wsi.setDocBase("classpath:/com/predic8/membrane/core/interceptor/administration/docBase");
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return flowController.invokeRequestHandlers(exc, interceptors);
	}

	@Override
	public void setRouter(Router router) {
		super.setRouter(router);
		r.setRouter(router);
		dapi.setRouter(router);
		wsi.setRouter(router);
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("adminConsole");
		if (dapi.isReadOnly())
			out.writeAttribute("readOnly", "true");
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) {
		if ( token.getAttributeValue("", "readOnly") != null ) {
			String v = token.getAttributeValue("", "readOnly");
			dapi.setReadOnly(Boolean.parseBoolean(v) || "1".equals(v));
		} else {
			dapi.setReadOnly(false);
		}
	}
	
	public boolean isReadOnly() {
		return dapi.isReadOnly();
	}
	
	public void setReadOnly(boolean readOnly) {
		dapi.setReadOnly(readOnly);
	}

	@Override
	public String getShortDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append("Displays the ");
		if (dapi.isReadOnly())
			sb.append("read-only ");
		sb.append("admin console.");
		return sb.toString();
	}
	
	@Override
	public String getHelpId() {
		return "admin-console";
	}
	
}
