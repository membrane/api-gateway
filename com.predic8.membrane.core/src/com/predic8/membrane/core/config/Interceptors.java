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

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.acl.AccessControlInterceptor;
import com.predic8.membrane.core.interceptor.administration.AdministrationInterceptor;
import com.predic8.membrane.core.interceptor.authentication.BasicAuthenticationInterceptor;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.interceptor.cbr.XPathCBRInterceptor;
import com.predic8.membrane.core.interceptor.rest.REST2SOAPInterceptor;
import com.predic8.membrane.core.interceptor.rewrite.RegExURLRewriteInterceptor;
import com.predic8.membrane.core.interceptor.schemavalidation.SOAPMessageValidatorInterceptor;
import com.predic8.membrane.core.interceptor.server.WebServerInterceptor;
import com.predic8.membrane.core.interceptor.statistics.*;
import com.predic8.membrane.core.interceptor.xslt.XSLTInterceptor;

public class Interceptors extends AbstractConfigElement {

	public static final String ELEMENT_NAME = "interceptors";

	private List<Interceptor> interceptors = new ArrayList<Interceptor>();

	public Interceptors(Router router) {
		super(router);
	}

	@Override
	protected String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child) throws XMLStreamException {
		if (AbstractInterceptor.ELEMENT_NAME.equals(child)) {
			interceptors.add(getInterceptorBId(readInterceptor(token).getId()));
		} else {
			interceptors.add(getInterceptor(router, token, child));
		}
	}
	
	private AbstractInterceptor readInterceptor(XMLStreamReader token)
			throws XMLStreamException {
		return (AbstractInterceptor) (new AbstractInterceptor(router)).parse(token);
	}

	private Interceptor getInterceptorBId(String id) {
		return router.getInterceptorFor(id);
	}

	public Interceptor getInterceptor(Router router, XMLStreamReader token, String name ) throws XMLStreamException {
		AbstractInterceptor i = null;
		
		if ("transform".equals(name)) {
			i = new XSLTInterceptor();
		} else if ("counter".equals(name)) {
			i = new CountInterceptor();
		} else if ("adminConsole".equals(name)) {
			i = new AdministrationInterceptor();
		} else if ("webServer".equals(name)) {
			i = new WebServerInterceptor();
		} else if ("balancer".equals(name)) {
			i = new LoadBalancingInterceptor();
		} else if ("clusterNotification".equals(name)) {
			i = new ClusterNotificationInterceptor();
		} else if ("regExUrlRewriter".equals(name)) {
			i = new RegExURLRewriteInterceptor();
		} else if ("soapValidator".equals(name)) {
			i = new SOAPMessageValidatorInterceptor();
		} else if ("rest2Soap".equals(name)) {
			i = new REST2SOAPInterceptor();
		} else if ("basicAuthentication".equals(name)) {
			i = new BasicAuthenticationInterceptor();
		} else if ("regExReplacer".equals(name)) {
			i = new RegExReplaceInterceptor();
		} else if ("cbr".equals(name)) {
			i = new XPathCBRInterceptor();
		} else if ("wsdlRewriter".equals(name)) {
			i = new WSDLInterceptor();
		} else if ("accessControl".equals(name)) {
			i = new AccessControlInterceptor();
		} else if ("statisticsCSV".equals(name)) {
			i = new StatisticsCSVInterceptor();
		} else if ("statisticsJDBC".equals(name)) {
			i = new StatisticsJDBCInterceptor();
		} else if ("exchangeStore".equals(name)) {
			i = new ExchangeStoreInterceptor();
		}
		i.setRouter(router);
		i.parse(token);
		return i;
	}	
	
	@Override
	public void write(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement(ELEMENT_NAME);
		for (Interceptor interceptor : interceptors) {
			interceptor.write(out);
		}
		out.writeEndElement();
	}

	public List<Interceptor> getInterceptors() {
		return interceptors;
	}

	public void setInterceptors(List<Interceptor> interceptors) {
		this.interceptors = interceptors;
	}

}
