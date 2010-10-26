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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.ws.relocator.Relocator;

public class WSDLInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(WSDLInterceptor.class.getName());
	
	private String host;
	
	private String protocol;
	
	private String port;
	
	private String registryWSDLRegisterURL;
	
	public WSDLInterceptor() {
		priority = 400;
	}
	
	public Outcome handleResponse(Exchange aExc) throws Exception {
		log.debug("handleResponse");
		
		HttpExchange exc = (HttpExchange)aExc;
		
		if ( exc.getRule() instanceof ProxyRule ) return  Outcome.CONTINUE;
		
		if (!wasGetRequest(exc)) 
			return Outcome.CONTINUE;
		
		if (!hasContent(exc)) 
			return Outcome.CONTINUE;
		
		
		if (!exc.getResponse().isXML())
			return Outcome.CONTINUE;
		
		log.debug("Changing endpoint address in WSDL");
			
		rewriteWsdl(exc);
		return Outcome.CONTINUE; 
	}


	private void rewriteWsdl(HttpExchange exc) throws Exception, IOException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		Relocator relocator = new Relocator(stream, getLocationProtocol(), getLocationHost(exc), getLocationPort(exc) );
		relocator.relocate(new ByteArrayInputStream(exc.getResponse().getBody().getContent()));
		if (relocator.isWsdlFound()) {
			registerWSDL(exc);
		}
		exc.getResponse().setBodyContent(stream.toByteArray());
	}


	private void registerWSDL(HttpExchange exc) {
		if (registryWSDLRegisterURL == null)
			return;
		
		StringBuffer buf = new StringBuffer();
		buf.append(registryWSDLRegisterURL);
		buf.append("?wsdl=");
		
		try {
			buf.append(URLDecoder.decode(getWSDLURL(exc), "US-ASCII"));
		} catch (UnsupportedEncodingException e) {
			//ignored
		}
		
		System.out.println(buf.toString());
	}

	private String getWSDLURL(HttpExchange exc) {
		StringBuffer buf = new StringBuffer();
		buf.append(getLocationProtocol());
		buf.append("://");
		buf.append(getLocationHost(exc));
		if (getLocationPort(exc) != 80) {
			buf.append(":");
			buf.append(getLocationPort(exc));
		}
		buf.append("/");
		buf.append(exc.getRequest().getUri());
		return buf.toString();
	}

	public void setRegistryWSDLRegisterURL(String registryWSDLRegisterURL) {
		this.registryWSDLRegisterURL = registryWSDLRegisterURL;
	}

	private boolean hasContent(Exchange exc) {
		return exc.getResponse().getHeader().getContentType() != null;
	}


	private boolean wasGetRequest(Exchange exc) {
		return Request.METHOD_GET.equals(exc.getRequest().getMethod());
	}


	private int getLocationPort(Exchange exc) {
		if ("".equals(port)) {
			return -1;
		}
		
		if (port != null)
			return Integer.parseInt(port);
		
		return exc.getRule().getKey().getPort();
	}
	

	private String getLocationHost(HttpExchange exc) {
		if (host != null)
			return host;
		
	    String locHost =  exc.getOriginalHostHeaderHost();
	    
	    log.debug("host " + locHost);
		
	    if (locHost == null) {
			return "localhost";
		}
	    
		return locHost;
	}
	
	private String getLocationProtocol() {
		if (protocol != null)
			return protocol;
		
		return "http";
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		log.debug("host property set for WSDL Interceptor: " + host);
		this.host = host;
	}

	public String getProtocol() {
		return protocol;
	}
	
	public void setProtocol(String protocol) {
		log.debug("protocol property set for WSDL Interceptor: " + protocol);
		this.protocol = protocol;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}
	
}
