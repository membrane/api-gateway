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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class AccessControlInterceptor extends AbstractInterceptor {

	protected static Log log = LogFactory.getLog(AccessControlInterceptor.class.getName());
	
	private String aclFilename;

	private AccessControl accessControl;

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Resource resource;
		try {
			resource = getAccessControl().getResourceFor(exc.getOriginalRequestUri());
		} catch (FileNotFoundException e) {
			log.warn("Could not find access control file: " + aclFilename );
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
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
		exc.setResponse(getResponse("Access denied: you are not authorized to access this service."));
	}

	private InetAddress getInetAddress(Exchange exc) {
		return exc.getServerThread().getSourceSocket().getInetAddress();
	}

	public void setAclFilename(String aclFilename) {
		this.aclFilename = aclFilename;
	}

	public AccessControl getAccessControl() throws Exception {
		if (accessControl == null) {
			init();
		}

		return accessControl;
	}

	private void init() throws Exception {
		if (aclFilename == null)
			throw new IllegalStateException("file name is not set");
		accessControl = parse(aclFilename);
	}

	protected AccessControl parse(String fileName) throws Exception {
		
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    XMLStreamReader reader = factory.createXMLStreamReader(new FileInputStream(fileName));
	    
	    return (AccessControl) new AccessControl(router).parse(reader);
	}
	
	public Response getResponse(String content) {
		Response response = new Response();
		response.setStatusCode(403);
		response.setStatusMessage("Forbidden");
		response.setVersion("1.1");

		Header header = new Header();
		header.add("Content-Type", "text;charset=UTF-8");
		response.setHeader(header);

		response.setBodyContent(content.getBytes());
		return response;
	}

}
