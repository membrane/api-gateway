/* Copyright 20010 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.acl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.FixedStreamReader;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ResourceResolver;

public class AccessControlInterceptor extends AbstractInterceptor {

	protected static Log log = LogFactory.getLog(AccessControlInterceptor.class.getName());
	
	private String aclFilename;

	private AccessControl accessControl;

	public AccessControlInterceptor() {
		setDisplayName("Access Control");
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Resource resource;
		try {
			resource = getAccessControl().getResourceFor(exc.getOriginalRequestUri());
		} catch (Exception e) {
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		if (!resource.checkAccess(getInetAddress(exc))) {
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		return Outcome.CONTINUE;
	}

	private void setResponseToAccessDenied(Exchange exc) throws IOException {
		exc.getRequest().getBody().read();
		exc.setResponse(Response.forbidden("Access denied: you are not authorized to access this service.").build());
	}

	private InetAddress getInetAddress(Exchange exc) {
		return exc.getServerThread().getSourceSocket().getInetAddress();
	}

	public void setAclFilename(String aclFilename) {
		this.aclFilename = aclFilename;
	}

	public String getAclFilename() {
		return aclFilename;
	}

	public AccessControl getAccessControl() throws Exception {
		if (accessControl == null) {
			init();
		}

		return accessControl;
	}

	private void init() throws Exception {
		accessControl = parse(aclFilename, router.getResourceResolver());
	}

	protected AccessControl parse(String fileName, ResourceResolver resourceResolver) throws Exception {
	    try {
			XMLInputFactory factory = XMLInputFactory.newInstance();
		    XMLStreamReader reader = new FixedStreamReader(factory.createXMLStreamReader(resourceResolver.resolve(fileName)));
		    return (AccessControl) new AccessControl(router).parse(reader);
	    } catch (Exception e) {
	    	log.error("Error initializing accessControl.", e);
	    	e.printStackTrace();
	    	System.err.println("Error initializing accessControl: terminating.");
	    	System.exit(1);
	    	throw e; // is never reached
	    }
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("accessControl");
		
		out.writeAttribute("file", aclFilename);
		
		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {		
		aclFilename = token.getAttributeValue("", "file");	
	}

	@Override
	public void doAfterParsing() throws Exception {
		init();
	}
	
	
}
