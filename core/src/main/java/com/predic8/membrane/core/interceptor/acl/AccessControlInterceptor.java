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
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.predic8.membrane.annot.Required;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.FixedStreamReader;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;

/**
 * @description Blocks requests whose origin TCP/IP address (hostname or IP address) is not allowed to access the
 *              requested resource.
 * @topic 6. Security
 */
@MCElement(name="accessControl")
public class AccessControlInterceptor extends AbstractInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AccessControlInterceptor.class.getName());

	private String file;

	private AccessControl accessControl;

	public AccessControlInterceptor() {
		setDisplayName("Access Control");
		setFlow(Flow.Set.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		Resource resource;
		try {
			resource = accessControl.getResourceFor(exc.getOriginalRequestUri());
		} catch (Exception e) {
			log.error("",e);
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		if (!resource.checkAccess(exc.getRemoteAddr(), exc.getRemoteAddrIp())) {
			setResponseToAccessDenied(exc);
			return Outcome.ABORT;
		}

		return Outcome.CONTINUE;
	}

	private void setResponseToAccessDenied(Exchange exc) throws IOException {
		exc.setResponse(Response.forbidden("Access denied: you are not authorized to access this service.").build());
	}

	/**
	 * @description Location of the ACL file.
	 * @example acl/acl.xml
	 */
	@Required
	@MCAttribute
	public void setFile(String file) {
		this.file = file;
	}

	public String getFile() {
		return file;
	}

	@Override
	public void init() throws Exception {
		accessControl = parse(file, router);
	}

	public AccessControl getAccessControl() {
		return accessControl;
	}

	protected AccessControl parse(String fileName, Router router) throws Exception {
		try {
			XMLInputFactory factory = XMLInputFactory.newInstance();
			XMLStreamReader reader = new FixedStreamReader(factory.createXMLStreamReader(router.getResolverMap()
					.resolve(ResolverMap.combine(router == null ? null : router.getBaseLocation(), fileName))));
			AccessControl res = (AccessControl) new AccessControl(router).parse(reader);
			res.init(router);
			return res;
		} catch (Exception e) {
			log.error("Error initializing accessControl.", e);
			System.err.println("Error initializing accessControl: terminating.");
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getShortDescription() {
		return "Authenticates incoming requests based on the file " + StringEscapeUtils.escapeHtml4(file) + " .";
	}

}
