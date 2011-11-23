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
import static com.predic8.membrane.core.util.URLParamUtil.createQueryString;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.stream.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.TextUtil;
import com.predic8.membrane.core.util.URLParamUtil;

public class AdminConsoleInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(AdminConsoleInterceptor.class
			.getName());

	public AdminConsoleInterceptor() {
		name = "Administration";
		setFlow(Flow.REQUEST);
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		log.debug("request: " + exc.getOriginalRequestUri());

		return dipatchRequest(exc);
		
	}

	@Mapping("/admin/?(\\?.*)?")
	public Response handleHomeRequest(Map<String, String> params) throws Exception {
		return respond(getServiceProxyPage(params));
	}

	@Mapping("/admin/proxy/?(\\?.*)?")
	public Response handleProxyRequest(Map<String, String> params) throws Exception {
		return respond(getProxyPage(params));
	}

	@Mapping("/admin/service-proxy/show/?(\\?.*)?")
	public Response handleServiceProxyShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		
		final ServiceProxy rule = (ServiceProxy) RuleUtil.findRuleByIdentifier(router,params.get("name"));
		
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 0;
			}
					
			@Override
			protected String getTitle() {
				return super.getTitle()+" "+rule.toString()+" ServiceProxy";
			}

			@Override
			protected void createTabContent() throws Exception {
				h1().text(rule.toString()+" ServiceProxy").end();
				table();
					createTr("Listen Port", "" + rule.getKey().getPort());
					createTr("Client Host", rule.getKey().getHost());
					createTr("Method", rule.getKey().getMethod());
					createTr("Path", rule.getKey().getPath());
					createTr("Target Host", rule.getTargetHost());
					createTr("Target Port", "" + rule.getTargetPort());
				end();
				h2().text("Status Codes").end();
				createStatusCodesTable(rule.getStatisticsByStatusCodes());
				h2().text("Configuration").end();
				pre().text(TextUtil.formatXML(new StringReader(rule.toXml()))).end();
			}
		}.createPage());
	}

	@Mapping("/admin/proxy/show/?(\\?.*)?")
	public Response handlePruleShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();

		final ProxyRule rule = (ProxyRule) RuleUtil.findRuleByIdentifier(router,params.get("name"));
		
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 1;
			}
		
			@Override
			protected String getTitle() {
				return super.getTitle()+" "+rule.toString()+" Proxy";
			}
			
			@Override
			protected void createTabContent() throws Exception {
				h1().text(rule.toString()+" Proxy").end();
				table();					
					createTr("Listen Port",""+rule.getKey().getPort());
				end();
				h2().text("Status Codes").end();
				createStatusCodesTable(rule.getStatisticsByStatusCodes());
				h2().text("Interceptors").end();
				createInterceptorTable(rule.getInterceptors());
			}
		
		}.createPage());
	}

	@Mapping("/admin/service-proxy/save/?(\\?.*)?")
	public Response handleFruleSaveRequest(Map<String, String> params) throws Exception {
		logAddFwdRuleParams(params);
		
		Rule r = new ServiceProxy(new ServiceProxyKey("*",
				params.get("method"), ".*", getPortParam(params)),
				params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
		
		return respond(getServiceProxyPage(params));
	}
	
	@Mapping("/admin/proxy/save/?(\\?.*)?")
	public Response handlePruleSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding proxy rule");
		log.debug("name: " + params.get("name"));
		log.debug("port: " + params.get("port"));
		
		Rule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port"))));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
		return respond(getProxyPage(params));
	}

	@Mapping("/admin/service-proxy/delete/?(\\?.*)?")
	public Response handleServiceProxyDeleteRequest(Map<String, String> params)
	  throws Exception {
			router.getRuleManager().removeRule(
					RuleUtil.findRuleByIdentifier(router, params.get("name")));
			return respond(getServiceProxyPage(params));
	}

	@Mapping("/admin/proxy/delete/?(\\?.*)?")
	public Response handleProxyDeleteRequest(Map<String, String> params)
	  throws Exception {
			router.getRuleManager().removeRule(
					RuleUtil.findRuleByIdentifier(router, params.get("name")));
			return respond(getProxyPage(params));
	}
	
	@Mapping("/admin/transport/?(\\?.*)?")
	public Response handleTransportRequest(Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 2;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Transport").end();
				
				h3().text("Transport Interceptors").end();
				createInterceptorTable(router.getTransport().getInterceptors());
			}
		
		}.createPage());
	}

	@Mapping("/admin/system/?(\\?.*)?")
	public Response handleSystemRequest(final Map<String, String> params)
   	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 3;
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

	@Mapping("/admin/clusters/?(\\?.*)?")
	public Response handleClustersRequest(Map<String, String> params) throws Exception {
		return respond(getClustersPage(params));
	}

	@Mapping("/admin/clusters/show/?(\\?.*)?")
	public Response handleClustersShowRequest(Map<String, String> params) throws Exception {
		return respond(getClusterPage(params));
	}

	@Mapping("/admin/clusters/save/?(\\?.*)?")
	public Response handleClustersSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding cluster");
		log.debug("name: " + params.get("name"));
		
		router.getClusterManager().addCluster(params.get("name"));
		
		return respond(getClustersPage(params));
	}

	@Mapping("/admin/node/show/?(\\?.*)?")
	public Response handleNodeShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 4;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Node " + params.get("host")+":"+params.get("port")).end();
				h3().text("Status Codes").end();
				Node n = router.getClusterManager().getNode(params.get("cluster"),
						params.get("host"),
						Integer.parseInt(params.get("port")));
				createStatusCodesTable(n.getStatisticsByStatusCodes());
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

	@Mapping("/admin/node/sessions/?(\\?.*)?")
	public Response handleNodeSessionsRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 4;
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
	
	@Mapping("/admin/node/save/?(\\?.*)?")
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

	@Mapping("/admin/node/up/?(\\?.*)?")
	public Response handleNodeUpRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().up(params.get("cluster"), params.get("host"),
				                      Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	@Mapping("/admin/node/takeout/?(\\?.*)?")
	public Response handleNodeTakeoutRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().takeout(params.get("cluster"), params.get("host"),
				                      Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	@Mapping("/admin/node/down/?(\\?.*)?")
	public Response handleNodeDownRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().down(params.get("cluster"), params.get("host"),
				                        Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}

	@Mapping("/admin/node/delete/?(\\?.*)?")
	public Response handleNodeDeleteRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().removeNode(params.get("cluster"), params.get("host"),
				                          Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("cluster",params.get("cluster")));
	}	

	@Mapping("/admin/node/reset/?(\\?.*)?")
	public Response handleNodeResetRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().getNode(params.get("cluster"), params.get("host"),
				                          Integer.parseInt(params.get("port"))).clearCounter();
		return redirect("node","show",createQueryString("cluster",params.get("cluster"),
														"host",params.get("host"),
														"port",params.get("port")));
	}	
	
	@Mapping("/admin/statistics")
	public Response handleStatisticsRequest(Map<String, String> params) throws Exception {
		return respond(getStatisticsPage(params));
	}
	
	private String getServiceProxyPage(Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 0;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h3().text("ServiceProxies").end();
				createFwdRulesTable();
				createAddFwdRuleForm();			
			}
	
		}.createPage();
	}

	private String getProxyPage(Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 1;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h3().text("Proxies").end();
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
				return 4;
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
				return 4;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h1().text("Clusters").end();
				createClustersTable();
				createAddClusterForm();				
			}
		
		}.createPage();
	}

	private String getStatisticsPage(Map<String, String> params)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 5;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h3().text("Statistics").end();
				createStatisticsTable();
			}
	
		}.createPage();
	}

	private Outcome dipatchRequest(Exchange exc) throws Exception {
		for (Method m : getClass().getMethods() ) {
			Mapping a = m.getAnnotation(Mapping.class);
			if ( a != null && Pattern.matches(a.value(), exc.getOriginalRequestUri())) {
				exc.setResponse((Response)m.invoke(this, new Object[] {getParams(exc)}));
				return Outcome.ABORT;
			}
		}
		return Outcome.CONTINUE;
	}

	private Map<String, String> getParams(Exchange exc) throws Exception {
		return URLParamUtil.getParams(exc);
	}

	private Response respond(String page) throws Exception {
		return createResponse(200, "OK", page, "text/html;charset=utf-8");
	}

	private Response redirect(String ctrl, String action, String query) throws Exception {
		return createResponse(302, "Found", null, "text/html;charset=utf-8",
				Header.LOCATION, AdminPageBuilder.createHRef(ctrl, action, query));
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
