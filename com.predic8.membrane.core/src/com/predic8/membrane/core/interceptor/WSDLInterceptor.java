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


package com.predic8.membrane.core.interceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.ws.relocator.Relocator;

public class WSDLInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WSDLInterceptor.class.getName());
	
	private String host;
	
	public Outcome handleResponse(Exchange exchange) throws Exception {
		log.debug("handleRequest");
		if ( exchange.getRule() instanceof ProxyRule ) return  Outcome.CONTINUE;
		
		if (!Request.METHOD_GET.equals(exchange.getRequest().getMethod())) 
			return Outcome.CONTINUE;
		
		if (exchange.getResponse().getHeader().getContentType() == null) 
			return Outcome.CONTINUE;
		
		
		if (!exchange.getResponse().isXML())
			return Outcome.CONTINUE;
		
		log.debug("Changing endpoint address in WSDL");
			
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		new Relocator(stream, getSourceHost(exchange), exchange.getRule().getRuleKey().getPort() ).relocate(new ByteArrayInputStream(exchange.getResponse().getBody().getContent()));
		exchange.getResponse().setBodyContent(stream.toByteArray());
		return Outcome.CONTINUE; 
	}
	

	private String getSourceHost(Exchange exchange) {
		if (host != null)
			return host;
		log.debug("Host header: "+exchange.getRequest().getHeader().getHost());
	    String sourcehost = ((String)exchange.getProperty(HttpTransport.HEADER_HOST)).replaceFirst(":.*", "");
	    log.debug("host " + sourcehost );
		if (sourcehost == null) {
			return "localhost";
		}
		return sourcehost;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	
	

}
