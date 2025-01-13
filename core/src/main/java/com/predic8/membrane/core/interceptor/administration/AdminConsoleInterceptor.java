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
package com.predic8.membrane.core.interceptor.administration;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.server.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;

/**
 * @description Displays up-to-date statistics, recent exchanges and, by default, allows live modification of Membrane's configuration.
 * @topic 5. Monitoring, Logging and Statistics
 */
@MCElement(name="adminConsole")
public class AdminConsoleInterceptor extends AbstractInterceptor {

	private final RewriteInterceptor rewriteInterceptor = new RewriteInterceptor();
	private final DynamicAdminPageInterceptor dapi = new DynamicAdminPageInterceptor();
	private final AdminRESTInterceptor rai = new AdminRESTInterceptor();
	private final WebServerInterceptor wsi = new WebServerInterceptor();

	// these are the interceptors this interceptor consists of
	private final List<Interceptor> interceptors = Arrays.asList(new Interceptor[] {rewriteInterceptor, rai, dapi, wsi });

	private boolean useXForwardedForAsClientAddr = false;

	public AdminConsoleInterceptor() {
		name = "Administration";
		rewriteInterceptor.getMappings().add(new RewriteInterceptor.Mapping("^/?$", "/admin", "redirect"));
		wsi.setDocBase("classpath:/com/predic8/membrane/core/interceptor/administration/docBase");
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		Outcome result = getFlowController().invokeRequestHandlers(exc, interceptors);

		if (exc.getRequest().getHeader().getFirstValue(X_REQUESTED_WITH) != null && exc.getResponse() != null)
			exc.getResponse().getHeader().add(EXPIRES, "-1");

		return result;
	}

	@Override
	public void init( {
		super.init();
		rewriteInterceptor.init(router);
		rai.setUseXForwardedForAsClientAddr(useXForwardedForAsClientAddr);
		rai.init(router);
		dapi.setUseXForwardedForAsClientAddr(useXForwardedForAsClientAddr);
		dapi.init(router);
		wsi.init(router);
	}

	public boolean isReadOnly() {
		return dapi.isReadOnly();
	}

	/**
	 * @description Whether runtime changes to Membrane's configuration can be committed in the admin console.
	 * @default false
	 */
	@MCAttribute
	public void setReadOnly(boolean readOnly) {
		rai.setReadOnly(readOnly);
		dapi.setReadOnly(readOnly);
	}

	public boolean isUseXForwardedForAsClientAddr() {
		return useXForwardedForAsClientAddr;
	}

	/**
	 * @description whether to show the value of the first "X-Forwarded-For" header instead of the client IP address
	 * @default false
	 */
	@MCAttribute
	public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
		this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
	}

	@Override
	public String getShortDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append("Displays the ");
		if (dapi.isReadOnly())
			sb.append("read-only ");
		sb.append("admin console.");
		return sb.toString();
	}
}
