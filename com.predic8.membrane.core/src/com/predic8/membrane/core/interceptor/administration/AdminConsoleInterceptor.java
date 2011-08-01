/* Copyright 2010 predic8 GmbH, www.predic8.com

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

import static com.predic8.membrane.core.util.HttpUtil.createResponse;
import static com.predic8.membrane.core.util.URLUtil.createQueryString;
import static com.predic8.membrane.core.util.URLUtil.parseQueryString;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.*;

import org.apache.commons.lang.WordUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ForwardingRuleKey;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;

public class AdminConsoleInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(AdminConsoleInterceptor.class
			.getName());

	private Pattern urlPattern = Pattern
			.compile("/admin/?([^/]*)(/[^/\\?]*)?(\\?.*)?");

	public AdminConsoleInterceptor() {
		name = "Administration";
		priority = 1000;
		setFlow(Flow.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		log.debug("request: " + exc.getOriginalRequestUri());

		return dipatchRequest(exc);
		
	}

	public Response handleRequest(Map<String, String> params) throws Exception {
		return respond(getRulesPage(params));
	}

	public Response handleFruleShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 0;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h1().text("Forwarding Rule Details").end();
				table();
					ServiceProxy rule = (ServiceProxy) RuleUtil
							.findRuleByIdentifier(router,
									params.get("name"));
					createTr("Name", rule.toString());
					createTr("Listen Port", "" + rule.getKey().getPort());
					createTr("Client Host", rule.getKey().getHost());
					createTr("Method", rule.getKey().getMethod());
					createTr("Path", rule.getKey().getPath());
					createTr("Target Host", rule.getTargetHost());
					createTr("Target Port", "" + rule.getTargetPort());
				end();
				h2().text("Interceptors").end();
				createInterceptorTable(rule.getInterceptors());
			}
		}.createPage());
	}

	public Response handlePruleShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 0;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h1().text("Proxy Rule Details").end();
				table();
					ProxyRule rule = (ProxyRule) RuleUtil.findRuleByIdentifier(router,params.get("name"));
					createTr("Name",rule.toString());
					createTr("Listen Port",""+rule.getKey().getPort());
				end();
				h2().text("Interceptors").end();
				createInterceptorTable(rule.getInterceptors());
			}
		
		}.createPage());
	}

	public Response handleFruleSaveRequest(Map<String, String> params) throws Exception {
		logAddFwdRuleParams(params);
		
		Rule r = new ServiceProxy(new ForwardingRuleKey("*",
				params.get("method"), ".*", getPortParam(params)),
				params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
		
		return respond(getRulesPage(params));
	}
	
	public Response handlePruleSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding proxy rule");
		log.debug("name: " + params.get("name"));
		log.debug("port: " + params.get("port"));
		
		Rule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port"))));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
		return respond(getRulesPage(params));
	}

	public Response handleRuleDeleteRequest(Map<String, String> params)
	  throws Exception {
			router.getRuleManager().removeRule(
					RuleUtil.findRuleByIdentifier(router, params.get("name")));
			return respond(getRulesPage(params));
	}
	
	public Response handleTransportRequest(Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 1;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Transport").end();
				
				h3().text("Backbone Interceptors").end();
				createInterceptorTable(router.getTransport().getBackboneInterceptors());
				
				h3().text("Transport Interceptors").end();
				createInterceptorTable(router.getTransport().getInterceptors());
			}
		
		}.createPage());
	}

	public Response handleSystemRequest(final Map<String, String> params)
   	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 2;
			}
		
			@Override
			protected void createTabContent() {
				h2().text("System").end();
				
				long total = Runtime.getRuntime().totalMemory();
				long free = Runtime.getRuntime().freeMemory();
				p().text("Availabe system memory: " + total).end();
				p().text("Free system memory: " + free).end();
			}
		}.createPage());
	}

	public Response handleClustersRequest(Map<String, String> params) throws Exception {
		return respond(getClustersPage(params));
	}

	public Response handleClustersShowRequest(Map<String, String> params) throws Exception {
		return respond(getClusterPage(params));
	}

	public Response handleClustersSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding cluster");
		log.debug("name: " + params.get("name"));
		
		router.getClusterManager().addCluster(params.get("name"));
		
		return respond(getClustersPage(params));
	}

	public Response handleNodeShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 3;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Node " + params.get("host")+":"+params.get("port")).end();
				h3().text("Status Codes").end();
				Node n = router.getClusterManager().getNode(params.get("cluster"),
						params.get("host"),
						Integer.parseInt(params.get("port")));
				createStatusNodes(n);
				p().text("Total requests: " + n.getCounter()).end();
				p().text("Current threads: " + n.getThreads()).end();
				p().text("Requests without responses: " + n.getLost()).end();
				span().classAttr("mb-button");
					createLink("Reset Counter", "node", "reset", createQueryString("cluster",params.get("cluster"),"host",n.getHost(),"port", ""+n.getPort()));
				end();
				span().classAttr("mb-button");
					createLink("Show Sessions", "node", "sessions", createQueryString("cluster",params.get("cluster"),"host",n.getHost(),"port", ""+n.getPort()));
				end();
			}
		}.createPage());
	}

	public Response handleNodeSessionsRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 3;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Node " + params.get("host")+":"+params.get("port")).end();
				h3().text("Sessions").end();
				createSessionsTable( router.getClusterManager().getSessionsByNode( params.get("cluster"),
									 new Node(params.get("host"), Integer.parseInt(params.get("port")))));
			}

		}.createPage());
	}
	
	public Response handleNodeSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding node");
		log.debug("cluster: " + params.get("cluster"));
		log.debug("host: " + params.get("host"));
		log.debug("port: " + params.get("port"));
		
		router.getClusterManager().up(params.get("cluster"),
				                      params.get("host"), 
				                      Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	public Response handleNodeUpRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().up(params.get("cluster"), params.get("host"),
				                      Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	public Response handleNodeTakeoutRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().takeout(params.get("cluster"), params.get("host"),
				                      Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	public Response handleNodeDownRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().down(params.get("cluster"), params.get("host"),
				                        Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	public Response handleNodeDeleteRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().removeNode(params.get("cluster"), params.get("host"),
				                          Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}	

	public Response handleNodeResetRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().getNode(params.get("cluster"), params.get("host"),
				                          Integer.parseInt(params.get("port"))).clearCounter();
		return redirect("node","show",createQueryString("cluster",params.get("cluster"),
														"host",params.get("host"),
														"port",params.get("port")));
	}	
	
	private String getRulesPage(Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 0;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Rules").end();
				
				h3().text("Forwarding Rules").end();
				createFwdRulesTable();
				createAddFwdRuleForm();			
				h3().text("Proxy Rules").end();
				createProxyRulesTable();
				createAddProxyRuleForm();
			}
	
		}.createPage();
	}

	private String getClusterPage(final Map<String, String> params)
  	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
			protected void createMetaElements() {
				createMeta("refresh", "5");				
			};
			
			@Override
			protected int getSelectedTab() {
				return 3;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h1().text("Cluster " + params.get("cluster")).end();
				createNodesTable();
				createAddNodeForm();				
			}
		
		}.createPage();
	}
	
	private String getClustersPage(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
		
			@Override
			protected int getSelectedTab() {
				return 3;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h1().text("Clusters").end();
				createClustersTable();
				createAddClusterForm();				
			}
		
		}.createPage();
	}
	 
	private Outcome dipatchRequest(Exchange exc) throws Exception {
		Matcher m = urlPattern.matcher(exc.getOriginalRequestUri());
		if (!m.matches()) return Outcome.CONTINUE;
		
		String action = m.group(2);
		String mName = "handle"+WordUtils.capitalize(m.group(1))+
						(action == null?"":WordUtils.capitalize(action.substring(1)))+"Request";
		
		exc.setResponse((Response)getClass().getMethod(mName, Map.class).invoke(this, new Object[] {getParams(exc)}));
		return Outcome.ABORT;
	}

	private Map<String, String> getParams(Exchange exc) throws Exception {
		URI jUri = new URI(exc.getOriginalRequestUri());
		String q = jUri.getQuery();
		if (q == null) {
			if (hasNoFormParams(exc))
				return new HashMap<String, String>();
			q = new String(exc.getRequest().getBody().getRaw());// TODO
																// getBody().toString()
																// doesn't work.
		}

		return parseQueryString(q);
	}

	private boolean hasNoFormParams(Exchange exc) throws IOException {
		return !"application/x-www-form-urlencoded".equals(exc.getRequest()
				.getHeader().getContentType())
				|| exc.getRequest().isBodyEmpty();
	}

	private Response respond(String page) throws Exception {
		return createResponse(200, "OK", page, "text/html;charset=utf-8");
	}

	private Response redirect(String ctrl, String action, String query) throws Exception {
		return createResponse(302, "Found", null, "text/html;charset=utf-8",
				"Location",AdminPageBuilder.createHRef(ctrl, action, query));
	}

	private int getPortParam(Map<String, String> params) {
		return Integer.parseInt(params.get("port"));
	}

	private int getTargetPortParam(Map<String, String> params) {
		return Integer.parseInt(params.get("targetPort"));
	}

	private void logAddFwdRuleParams(Map<String, String> params) {
		log.debug("adding fwd rule");
		log.debug("name: " + params.get("name"));
		log.debug("port: " + params.get("port"));
		log.debug("client host: " + params.get("clientHost"));
		log.debug("method: " + params.get("method"));
		log.debug("path: " + params.get("path"));
		log.debug("target host: " + params.get("targetHost"));
		log.debug("target port: " + params.get("targetPort"));
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		
		out.writeStartElement("adminConsole");		
		out.writeEndElement();
	}

}
