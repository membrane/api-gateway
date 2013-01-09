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
package com.predic8.membrane.core.interceptor.acl;

import java.io.IOException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.util.HtmlUtils;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.FixedStreamReader;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ResourceResolver;

@MCElement(name="accessControl")
public class AccessControlInterceptor extends AbstractInterceptor {

	private static final Log log = LogFactory.getLog(AccessControlInterceptor.class.getName());
	
	private String file;

	private AccessControl accessControl;

	public AccessControlInterceptor() {
		setDisplayName("Access Control");
		setFlow(Flow.REQUEST);
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Resource resource;
		try {
			resource = accessControl.getResourceFor(exc.getOriginalRequestUri());
		} catch (Exception e) {
			log.error(e);
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		if (!resource.checkAccess(exc.getHandler().getRemoteAddress())) {
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		return Outcome.CONTINUE;
	}

	private void setResponseToAccessDenied(Exchange exc) throws IOException {
		exc.setResponse(Response.forbidden("Access denied: you are not authorized to access this service.").build());
	}

	@Required
	@MCAttribute
	public void setFile(String file) {
		this.file = file;
	}

	public String getFile() {
		return file;
	}

	public void init() throws Exception {
		accessControl = parse(file, router.getResourceResolver());
	}
	
	public AccessControl getAccessControl() {
		return accessControl;
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
	    	throw new RuntimeException(e);
	    }
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("accessControl");
		
		out.writeAttribute("file", file);
		
		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {		
		file = token.getAttributeValue("", "file");	
	}

	@Override
	public String getShortDescription() {
		return "Authenticates incoming requests based on the file " + HtmlUtils.htmlEscape(file) + " .";
	}
	
	@Override
	public String getHelpId() {
		return "access-control";
	}
	
}
