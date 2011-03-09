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
package com.predic8.membrane.core.interceptor.acl;

import java.io.IOException;
import java.net.InetAddress;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.HttpExchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

public class AccessControlInterceptor extends AbstractInterceptor {

	private String aclFilename;

	private AccessControl accessControl;

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Service service;
		try {
			service = getAccessControl().getServiceFor(((HttpExchange) exc).getOriginalRequestUri());
		} catch (Exception e) {
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		if (!service.checkAccess(getInetAddress((HttpExchange) exc))) {
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		return Outcome.CONTINUE;
	}

	private void setResponseToAccessDenied(Exchange exc) throws IOException {
		exc.getRequest().getBody().read();
		exc.setResponse(getResponse("Access denied: you are not authorized to access this service."));
	}

	private InetAddress getInetAddress(HttpExchange exc) {
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
		accessControl = new AccessControlParser().read(aclFilename);
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
