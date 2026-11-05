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
import com.predic8.membrane.core.interceptor.administration.v1.*;
import com.predic8.membrane.core.interceptor.rewrite.*;
import com.predic8.membrane.core.interceptor.server.*;

import java.util.*;

import static com.predic8.membrane.core.http.Header.*;

/**
 * @description Displays up-to-date statistics, recent exchanges and, by default, allows live modification of Membrane's configuration.
 * <p>See <a href="https://github.com/membrane/api-gateway/blob/master/distribution/tutorials/getting-started/70-Admin-Web-Console.yaml">tutorials/getting-started/70-Admin-Web-Console.yaml</a>.</p>
 * @yaml <pre><code>
 * api:
 *   port: 9000
 *   flow:
 *     - adminConsole: {}
 * </code></pre>
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name="adminConsole")
public class AdminConsoleInterceptor extends AbstractInterceptor {

	private final RewriteInterceptor rewriteInterceptor = new RewriteInterceptor();
	private final AdminApiV1Interceptor apiV1 = new AdminApiV1Interceptor();
	private final DynamicAdminPageInterceptor dapi = new DynamicAdminPageInterceptor();
	private final AdminRESTInterceptor rai = new AdminRESTInterceptor();
	private final WebServerInterceptor wsi = new WebServerInterceptor();

	// these are the interceptors this interceptor consists of
	private final List<Interceptor> interceptors = Arrays.asList(new Interceptor[] {rewriteInterceptor, apiV1, rai, dapi, wsi });

	private boolean useXForwardedForAsClientAddr = false;

	/**
	 * The new web UI, packaged separately (module admin-ui). Without it on the classpath only the old UI is served.
	 */
	static final String CONSOLE_INDEX = "/com/predic8/membrane/core/interceptor/administration/docBase/console/index.html";

	public AdminConsoleInterceptor() {
		name = "administration";
		final var hasConsole = AdminConsoleInterceptor.class.getResource(CONSOLE_INDEX) != null;
		rewriteInterceptor.getMappings().add(new RewriteInterceptor.Mapping("^/?$", hasConsole ? "/console/" : "/admin", "redirect"));
		rewriteInterceptor.getMappings().add(new RewriteInterceptor.Mapping("^/console$", "/console/", "redirect"));
		wsi.setDocBase("classpath:/com/predic8/membrane/core/interceptor/administration/docBase");
		wsi.setIndex("index.html");
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		Outcome result = getFlowController().invokeRequestHandlers(exc, interceptors);

		if (exc.getRequest().getHeader().getFirstValue(X_REQUESTED_WITH) != null && exc.getResponse() != null)
			exc.getResponse().getHeader().add(EXPIRES, "-1");

		return result;
	}

	@Override
	public void init() {
		super.init();
		rewriteInterceptor.init(router);
		apiV1.setUseXForwardedForAsClientAddr(useXForwardedForAsClientAddr);
		apiV1.init(router);
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
		apiV1.setReadOnly(readOnly);
		rai.setReadOnly(readOnly);
		dapi.setReadOnly(readOnly);
	}

	public String getInstanceName() {
		return apiV1.getInstanceName();
	}

	/**
	 * @description Name of this Membrane instance in the admin API, for example to tell redundant instances apart.
	 * @default the host name
	 */
	@MCAttribute
	public void setInstanceName(String instanceName) {
		apiV1.setInstanceName(instanceName);
	}

	public String getApiKey() {
		return apiV1.getApiKey();
	}

	/**
	 * @description If set, requests to the admin API (<code>/admin/api/v1</code>) must carry this key in the
	 * <code>X-Api-Key</code> header. Set it on instances that are included as <code>peers</code> of another admin
	 * console.
	 */
	@MCAttribute
	public void setApiKey(String apiKey) {
		apiV1.setApiKey(apiKey);
	}

	public List<AdminConsolePeer> getPeers() {
		return apiV1.getPeers();
	}

	/**
	 * @description Other Membrane instances to include, for example redundant instances behind a load balancer. The
	 * console then lists the APIs and calls of all instances together.
	 */
	@MCChildElement
	public void setPeers(List<AdminConsolePeer> peers) {
		apiV1.setPeers(peers);
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
