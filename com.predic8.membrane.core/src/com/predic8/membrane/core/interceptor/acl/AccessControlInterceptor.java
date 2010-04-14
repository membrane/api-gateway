package com.predic8.membrane.core.interceptor.acl;

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
		Service service = getAccessControl().getServiceFor(((HttpExchange) exc).getRequestUri()) ;
		if (service == null) {
			exc.getRequest().getBody().read();
			exc.setResponse(getResponse("No matching service found for request URI."));
			return Outcome.ABORT;
		}

		if (!serviceEnabled(service, (HttpExchange) exc) ) {
			exc.getRequest().getBody().read();
			exc.setResponse(getResponse("Access denied: you are not authorized to access this service."));
			return Outcome.ABORT;
		}
		
		return Outcome.CONTINUE;
	}
	
	private boolean serviceEnabled(Service service, HttpExchange exc) {
		return service.checkAccess(exc.getServerThread().getSourceSocket().getInetAddress());
	}

	public String getAclFilename() {
		return aclFilename;
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
