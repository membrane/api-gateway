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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.apache.commons.text.*;
import org.slf4j.*;

import javax.xml.stream.*;
import java.net.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.HttpUtil.*;

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

	private boolean useXForwardedForAsClientAddr = false;

	public AccessControlInterceptor() {
		setDisplayName("access control");
		setFlow(REQUEST_FLOW);
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		var remoteAddr = exc.getRemoteAddr();
		var remoteAddrIp = exc.getRemoteAddrIp();

		var xff = getForwardedForList(exc);
		if (useXForwardedForAsClientAddr && !xff.isEmpty()) {
			var xLast = xff.getLast();
			try {
				remoteAddrIp = InetAddress.getByName(xLast).getHostAddress();
			} catch (UnknownHostException e) {
				remoteAddr = xLast;
			}
		}

		Resource resource;
		try {
			resource = accessControl.getResourceFor(exc.getOriginalRequestUri());
		} catch (Exception e) {
			log.error("",e);
			setResponseToAccessDenied(exc);
			return ABORT;
		}

		if (!resource.checkAccess(remoteAddr, remoteAddrIp)) {
			setResponseToAccessDenied(exc);
			return ABORT;
		}
		return CONTINUE;
	}

	private void setResponseToAccessDenied(Exchange exc) {
		log.warn("Access Denied. Method: {} Uri: {}", exc.getRequest().getMethod(), exc.getOriginalRequestUri());
		security(false,getDisplayName())
				.title("Access Denied")
				.statusCode(401)
				.addSubSee("authorization-denied")
				.buildAndSetResponse(exc);
	}

	/**
	 * @description whether to use the last value of the last "X-Forwarded-For" header instead of the remote IP address
	 * @default false
	 */
	@MCAttribute
	public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
		this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
	}

	public boolean isUseXForwardedForAsClientAddr() {
		return useXForwardedForAsClientAddr;
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
	public void init() {
		super.init();
		accessControl = parse(file, router);
	}

	public void setAccessControl(AccessControl ac) { accessControl = ac; }

	protected AccessControl parse(String fileName, Router router) {
		try {
			XMLInputFactory factory = XMLInputFactory.newInstance();
			XMLStreamReader reader = new FixedStreamReader(factory.createXMLStreamReader(router.getResolverMap()
					.resolve(ResolverMap.combine(router.getBaseLocation(), fileName))));
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