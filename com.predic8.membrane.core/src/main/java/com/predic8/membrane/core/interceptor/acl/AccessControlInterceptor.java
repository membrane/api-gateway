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
		if (aclFilename == null) {
			log.error("Fatal error in proxy configuration: <accessControl> element is invalid.");
			log.error("File name is not specified for access control. The valid element looks like <accessControl file='conf/acl.xml'/>");
			System.exit(1);
		}
		accessControl = parse(aclFilename);
	}

	protected AccessControl parse(String fileName) throws Exception {
		XMLInputFactory factory = XMLInputFactory.newInstance();
	    XMLStreamReader reader = new FixedStreamReader(factory.createXMLStreamReader(new FileInputStream(checkAclFile(fileName))));
	    return (AccessControl) new AccessControl(router).parse(reader);
	}
	
	private String checkAclFile(String fileName) {
		File file = new File(fileName);
		if (file.exists()) {
			return fileName;
		}
		
		fileName = System.getenv(Constants.MEMBRANE_HOME) + System.getProperty("file.separator") + fileName;
		file = new File(fileName);
		if (!file.exists()) {
			log.error("Error in proxy configuration: <accessControl> element may contain invalid data.");
			log.error("Unable to locate access control file "  + fileName + ". Please set MEMBRANE_HOME or start the Membrane from the instalation directory.");
			System.exit(1);
		}
		
		return fileName;
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
	protected void doAfterParsing() throws Exception {
		init();
	}
	
	
}
