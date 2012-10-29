/* Copyright 2012 predic8 GmbH, www.predic8.com

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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.ProxyRuleKey;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.util.DateUtil;
import com.predic8.membrane.core.util.TextUtil;
import com.predic8.membrane.core.util.URLParamUtil;

/**
 * Handles the dynamic part of the admin console (= requests starting with "/admin/"). 
 */
public class DynamicAdminPageInterceptor extends AbstractInterceptor {
	private static Log log = LogFactory.getLog(DynamicAdminPageInterceptor.class.getName());
	private boolean readOnly;

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		log.debug("request: " + exc.getOriginalRequestUri());

		exc.setTimeReqSent(System.currentTimeMillis());
		
		Outcome o = dispatchRequest(exc);
		
		exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());

		return o;
	}

	@Mapping("/admin/?(\\?.*)?")
	public Response handleHomeRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@Mapping("/admin/proxy/?(\\?.*)?")
	public Response handleProxyRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getProxyPage(params, relativeRootPath));
	}

	@Mapping("/admin/service-proxy/show/?(\\?.*)?")
	public Response handleServiceProxyShowRequest(final Map<String, String> params, final String relativeRootPath)
	  throws Exception {
		final StringWriter writer = new StringWriter();
		
		final ServiceProxy rule = (ServiceProxy) RuleUtil.findRuleByIdentifier(router,params.get("name"));
		
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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
				script().raw("$(function() {\r\n" + 
						"					$( \"#subtab\" ).tabs();\r\n" + 
						"				});").end();
				
				div().id("subtab");
					ul();
						li().a().href("#tab1").text("Visualization").end(2);
						li().a().href("#tab2").text("Statistics").end(2);
						li().a().href("#tab3").text("XML Configuration").end(2);
					end();
					div().id("tab1");
						createServiceProxyVisualization(rule, relativeRootPath);
					end();
					div().id("tab2");
						createStatusCodesTable(rule.getStatisticsByStatusCodes());
						br();
						createButton("View Messages", "calls", null, createQueryString("proxy", rule.toString()));
					end();
					div().id("tab3");
						div().classAttr("proxy-config");
						String xml = "";
						try {
							xml = rule.toXml();
							xml = TextUtil.formatXML(new StringReader(xml), true);
							raw(xml);
						} catch (Exception e) {
							log.error(xml);
							e.printStackTrace();
						}
						end();
					end();
				end();
			}
		}.createPage());
	}

	@Mapping("/admin/proxy/show/?(\\?.*)?")
	public Response handlePruleShowRequest(final Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();

		final ProxyRule rule = (ProxyRule) RuleUtil.findRuleByIdentifier(router,params.get("name"));
		
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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
				if (rule.getKey().getPort() != -1) {
					table();					
						createTr("Listen Port",""+rule.getKey().getPort());
					end();
				}
				h2().text("Status Codes").end();
				createStatusCodesTable(rule.getStatisticsByStatusCodes());
				h2().text("Interceptors").end();
				createInterceptorTable(rule.getInterceptors());
			}
		
		}.createPage());
	}

	@Mapping("/admin/service-proxy/save/?(\\?.*)?")
	public Response handleServiceProxySaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		logAddFwdRuleParams(params);
		
		Rule r = new ServiceProxy(new ServiceProxyKey("*",
				params.get("method"), ".*", getPortParam(params)),
				params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		router.getRuleManager().addProxyAndOpenPortIfNew(r);
		
		return respond(getServiceProxyPage(params, relativeRootPath));
	}
	
	@Mapping("/admin/proxy/save/?(\\?.*)?")
	public Response handleProxySaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		log.debug("adding proxy rule");
		log.debug("name: " + params.get("name"));
		log.debug("port: " + params.get("port"));
		
		Rule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port"))));
		r.setName(params.get("name"));
		router.getRuleManager().addProxyAndOpenPortIfNew(r);
		return respond(getProxyPage(params, relativeRootPath));
	}

	@Mapping("/admin/service-proxy/delete/?(\\?.*)?")
	public Response handleServiceProxyDeleteRequest(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		router.getRuleManager().removeRule(
				RuleUtil.findRuleByIdentifier(router, params.get("name")));
		return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@Mapping("/admin/proxy/delete/?(\\?.*)?")
	public Response handleProxyDeleteRequest(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		router.getRuleManager().removeRule(
				RuleUtil.findRuleByIdentifier(router, params.get("name")));
		return respond(getProxyPage(params, relativeRootPath));
	}
	
	@Mapping("/admin/transport/?(\\?.*)?")
	public Response handleTransportRequest(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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
	public Response handleSystemRequest(final Map<String, String> params, String relativeRootPath)
   	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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
				
				p().text("Membrane version: " + Constants.VERSION).end();
			}
		}.createPage());
	}

	@Mapping("/admin/balancers/?(\\?.*)?")
	public Response handleBalancersRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getBalancersPage(params, relativeRootPath));
	}
	
	@Mapping("/admin/clusters/?(\\?.*)?")
	public Response handleClustersRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getClustersPage(params, relativeRootPath));
	}

	@Mapping("/admin/clusters/show/?(\\?.*)?")
	public Response handleClustersShowRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getClusterPage(params, relativeRootPath));
	}

	@Mapping("/admin/clusters/save/?(\\?.*)?")
	public Response handleClustersSaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		log.debug("adding cluster");
		log.debug("balancer: " + getBalancerParam(params));
		log.debug("name: " + params.get("name"));
		
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).addCluster(params.get("name"));
		
		return respond(getClustersPage(params, relativeRootPath));
	}

	@Mapping("/admin/node/show/?(\\?.*)?")
	public Response handleNodeShowRequest(final Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return 4;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				String balancer = getBalancerParam(params);
				h2().text("Node " + params.get("host")+":"+params.get("port") + " (" +
						"Cluster " + params.get("cluster") + " of Balancer " + balancer + ")").end();
				h3().text("Status Codes").end();
				Node n = BalancerUtil.lookupBalancer(router, balancer).getNode(
						params.get("cluster"),
						params.get("host"),
						Integer.parseInt(params.get("port")));
				createStatusCodesTable(n.getStatisticsByStatusCodes());
				p().text("Total requests: " + n.getCounter()).end();
				p().text("Current threads: " + n.getThreads()).end();
				p().text("Requests without responses: " + n.getLost()).end();
				span().classAttr("mb-button");
					createLink("Reset Counter", "node", "reset", createQueryString("balancer", balancer, "cluster",params.get("cluster"),"host",n.getHost(),"port", ""+n.getPort()));
				end();
				span().classAttr("mb-button");
					createLink("Show Sessions", "node", "sessions", createQueryString("balancer", balancer, "cluster",params.get("cluster"),"host",n.getHost(),"port", ""+n.getPort()));
				end();
			}
		}.createPage());
	}

	@Mapping("/admin/node/sessions/?(\\?.*)?")
	public Response handleNodeSessionsRequest(final Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return 4;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h2().text("Node " + params.get("host")+":"+params.get("port")).end();
				h3().text("Sessions").end();
				createSessionsTable( BalancerUtil.lookupBalancer(router, getBalancerParam(params)).getSessionsByNode(
						params.get("cluster"),
						new Node(params.get("host"), Integer.parseInt(params.get("port")))));
			}

		}.createPage());
	}
	
	@Mapping("/admin/node/save/?(\\?.*)?")
	public Response handleNodeSaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		log.debug("adding node");
		log.debug("balancer: " + getBalancerParam(params));
		log.debug("cluster: " + params.get("cluster"));
		log.debug("host: " + params.get("host"));
		log.debug("port: " + params.get("port"));
		
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).up(
				params.get("cluster"),
				params.get("host"), 
				Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("balancer", getBalancerParam(params), "cluster", params.get("cluster")), relativeRootPath);
	}

	@Mapping("/admin/node/up/?(\\?.*)?")
	public Response handleNodeUpRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).up(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@Mapping("/admin/node/takeout/?(\\?.*)?")
	public Response handleNodeTakeoutRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).takeout(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@Mapping("/admin/node/down/?(\\?.*)?")
	public Response handleNodeDownRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).down(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@Mapping("/admin/node/delete/?(\\?.*)?")
	public Response handleNodeDeleteRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();
		
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).removeNode(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters","show",createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}	

	@Mapping("/admin/node/reset/?(\\?.*)?")
	public Response handleNodeResetRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).getNode(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port"))).clearCounter();
		return redirect("node","show",createQueryString("balancer", getBalancerParam(params),
														"cluster",params.get("cluster"),
														"host",params.get("host"),
														"port",params.get("port")), relativeRootPath);
	}	
	
	@Mapping("/admin/statistics")
	public Response handleStatisticsRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getStatisticsPage(params, relativeRootPath));
	}
	
	@Mapping("/admin/calls(/?\\?.*)?")
	public Response handleCallsRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getCallsPage(params, relativeRootPath));
	}

	@Mapping("/admin/call/?(\\?.*)?")
	public Response handleCallRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getCallPage(params, relativeRootPath));
	}

	@Mapping("/admin/clients")
	public Response handleClientsRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getClientsPage(params, relativeRootPath));
	}

	@Mapping("/admin/about")
	public Response getAbout(Map<String, String> params, String relativeRootPath) throws Exception {
		return respond(getAboutPage(params, relativeRootPath));
	}

	private String getServiceProxyPage(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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

	private String getProxyPage(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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

	private String getClusterPage(final Map<String, String> params, String relativeRootPath)
  	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			protected void createMetaElements() {
				createMeta("refresh", "5");				
			};
			
			@Override
			protected int getSelectedTab() {
				return 4;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				String balancer = getBalancerParam(params);
				h2().text("Cluster " + params.get("cluster") + " of Balancer " + balancer).end();
				createNodesTable(balancer);
				createAddNodeForm(balancer);				
			}
		
		}.createPage();
	}
	
	private String getClustersPage(final Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
		
			@Override
			protected int getSelectedTab() {
				return 4;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				String balancer = getBalancerParam(params);
				h1().text("Balancer " + balancer).end();
				h2().text("Clusters").end();
				createClustersTable(balancer);
				createAddClusterForm(balancer);
				p();
					text("Failover: ");
					text(BalancerUtil.lookupBalancerInterceptor(router, balancer).isFailOver() ? "yes" : "no");
				end();
			}
		
		}.createPage();
	}

	private String getBalancersPage(final Map<String, String> params, String relativeRootPath)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
				
			@Override
			protected int getSelectedTab() {
				return 4;
			}

			@Override
			protected void createTabContent() throws Exception {
				h1().text("Balancers").end();
				createBalancersTable();
			}

		}.createPage();
	}

	private String getStatisticsPage(Map<String, String> params, String relativeRootPath)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
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

	private String getCallsPage(final Map<String, String> params, String relativeRootPath)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return 6;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				PropertyValueCollector propertyValues = new PropertyValueCollector();
				
				router.getExchangeStore().collect(propertyValues);
				h3().text("Filter").end();
				form();
					div();
						span().text("Method").end()
						.select().id("message-filter-method")
						.onchange("membrane.messageTable.fnDraw();");
							option("*", "*", true);	
							for (String s : sort(propertyValues.getMethods())) {
								option(s, s, false);	
							}						
						end();
						span().text("Status Code").end()
						.select().id("message-filter-statuscode")
						.onchange("membrane.messageTable.fnDraw();");
							option("*", "*", true);	
							for (Integer i : sort(propertyValues.statusCodes)) {
								option(i.toString(), i.toString(), false);	
							}
					end(2);					
					div();
						span().text("Proxy").end()
						.select().id("message-filter-proxy")
						.onchange("membrane.messageTable.fnDraw();");
							option("*", "*", !params.containsKey("proxy"));	
							for (String s : sort(propertyValues.getProxies())) {
								option(s, s, s.equals(params.get("proxy")));	
							}
						end();
	 					span().text("Client").end()
	 					.select().id("message-filter-client")
	 					.onchange("membrane.messageTable.fnDraw();");
	 						option("*", "*", !params.containsKey("client"));	
	 						for (String s : sort(propertyValues.getClients())) {
	 							option(s, s, s.equals(params.get("client")));	
	 						}
	 					end();
 						span().text("Server").end()
 						.select().id("message-filter-server")
 						.onchange("membrane.messageTable.fnDraw();");
 							option("*", "*", true);	
 							for (String s : sort(propertyValues.getServers())) {
 								option(s==null?"undefined":s, s==null?"":s, false);	
 							}						
					end(2);					
					div();
						span().text("Request Content-Type").end()
						.select().id("message-filter-reqcontenttype")
						.onchange("membrane.messageTable.fnDraw();");
							option("*", "*", true);	
							for (String s : sort(propertyValues.getReqContentTypes())) {
								option(s.isEmpty()?"undefined":s, s, false);	
							}						
						end();					
						span().text("Response Content-Type").end()
						.select().id("message-filter-respcontenttype")
						.onchange("membrane.messageTable.fnDraw();");
							option("*", "*", true);	
							for (String s : sort(propertyValues.getRespContentTypes())) {
								option(s.isEmpty()?"undefined":s, s, false);	
							}						
					end(2);	
					br();
					createButton("Reset Filter", "calls", null, null);
				end();
				h3().text(getMessagesText()).end();
				createMessageStatisticsTable();
			}

			private String getMessagesText() {
				if (router.getExchangeStore() instanceof LimitedMemoryExchangeStore) {
					LimitedMemoryExchangeStore lmes = (LimitedMemoryExchangeStore)router.getExchangeStore();
					float usage = 100.0f * lmes.getCurrentSize() / lmes.getMaxSize();
					Long oldestTimeResSent = lmes.getOldestTimeResSent();
					String usageStr =
							oldestTimeResSent == null ? "" :
								String.format("; usage %.0f%%; the last %s", usage,
										DateUtil.prettyPrintTimeSpan(System.currentTimeMillis() - oldestTimeResSent));
					return "Messages" + String.format(
							" (limited to last %.2f MB%s)",
							lmes.getMaxSize()/1000000., 
							usageStr);
				}
				return "Messages";
			}
	
		}.createPage();
	}
	
	private <T extends Comparable<? super T>> List<T> sort(Set<T> data) {
		int nulls = 0;
		ArrayList<T> res = new ArrayList<T>(data.size());
		for (T t : data)
			if (t == null)
				nulls++;
			else
				res.add(t);
		Collections.sort(res);
		while (nulls-- > 0)
			res.add(0, null);
		return res;
	}

	private String getCallPage(final Map<String, String> params, String relativeRootPath)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return 6;
			}
					
			@Override
			protected void createTabContent() throws Exception {
				script().raw("$(function() {\r\n" + 
						"					$( \"#exchangeTab\" ).tabs();\r\n" +
						"					$( \"#requestContentTab\" ).tabs();\r\n" +
						"					$( \"#responseContentTab\" ).tabs();\r\n" +
						"                   membrane.loadExchange("+params.get("id")+");\r\n"+
						"				});").end();
				
				div().id("exchangeTab");
					ul();
						li().a().href("#tab1").text("Request").end(2);
						li().a().href("#tab2").text("Response").end(2);
					end();
					div().id("tab1");
						creatExchangeMetaTable("request-meta");
						h3().text("Content").end();
						div().align("right").a().id("request-download-button").text("Download").end().end();
						div().id("requestContentTab");
							ul();
								li().a().href("#requestRawTab").text("Raw").end(2);
								li().a().href("#requestHeadersTab").text("Headers").end(2);
								li().a().href("#requestBodyTab").text("Body").end(2);
							end();
							div().id("requestRawTab");
								div().id("request-raw").classAttr("proxy-config").end();
							end();
							div().id("requestHeadersTab");
								creatHeaderTable("request-headers");
							end();
							div().id("requestBodyTab");
								div().id("request-body").classAttr("proxy-config").end();
							end();
						end();
					end();
					div().id("tab2");
						creatExchangeMetaTable("response-meta");					
						h3().text("Content").end();
						div().align("right").a().id("response-download-button").text("Download").end().end();
						div().id("responseContentTab");
							ul();
								li().a().href("#responseRawTab").text("Raw").end(2);
								li().a().href("#responseHeadersTab").text("Headers").end(2);
								li().a().href("#responseBodyTab").text("Body").end(2);
							end();
							div().id("responseRawTab");
								div().id("response-raw").classAttr("proxy-config").end();
							end();
							div().id("responseHeadersTab");
								creatHeaderTable("response-headers");
							end();
							div().id("responseBodyTab");
								div().id("response-body").classAttr("proxy-config").end();
							end();
						end();
					end();
				end();
			}
	
		}.createPage();
	}

	private String getClientsPage(Map<String, String> params, String relativeRootPath)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return 7;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h3().text("Statistics").end();
				createClientsStatisticsTable();
			}
	
		}.createPage();
	}

	private String getAboutPage(final Map<String, String> params, String relativeRootPath)
			  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return 8;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h3().text("Impressum").end();
				p().text("predic8 GmbH").br().text("Moltkestr. 40").br().br().text("53173 Bonn").end();
			}
	
		}.createPage();
	}
	
	private Outcome dispatchRequest(Exchange exc) throws Exception {
		String pathQuery = URIUtil.getPathQuery(exc.getDestinations().get(0));
		for (Method m : getClass().getMethods() ) {
			Mapping a = m.getAnnotation(Mapping.class);
			if ( a != null && Pattern.matches(a.value(), pathQuery)) {
				exc.setResponse((Response)m.invoke(this, new Object[] { getParams(exc), getRelativeRootPath(pathQuery) }));
				return Outcome.RETURN;
			}
		}
		return Outcome.CONTINUE;
	}

	private Map<String, String> getParams(Exchange exc) throws Exception {
		return URLParamUtil.getParams(exc);
	}
	
	/**
	 * For example, returns "../.." for the input "/admin/clusters/".
	 */
	private String getRelativeRootPath(String pathQuery) throws MalformedURLException {
		String path = URIUtil.getPath(pathQuery);
		// count '/'s
		int depth = 0;
		for (int i = 0; i < path.length(); i++)
			if (path.charAt(i) == '/')
				depth++;
		// remove leading '/'
		if (depth > 0)
			depth--;
		// build relative path for depth
		StringBuilder relativeRootPath = new StringBuilder();
		if (depth == 0)
			relativeRootPath.append(".");
		else
			for (; depth>0; depth--)
				if (depth == 1)
					relativeRootPath.append("..");
				else
					relativeRootPath.append("../");
		return relativeRootPath.toString();
	}

	private Response respond(String page) throws Exception {
		return createResponse(200, "OK", page, MimeType.TEXT_HTML_UTF8);
	}

	private Response redirect(String ctrl, String action, String query, String relativeRootPath) throws Exception {
		return createResponse(302, "Found", null, MimeType.TEXT_HTML_UTF8,
				Header.LOCATION, relativeRootPath + AdminPageBuilder.createHRef(ctrl, action, query));
	}

	private int getPortParam(Map<String, String> params) {
		return Integer.parseInt(params.get("port"));
	}

	private int getTargetPortParam(Map<String, String> params) {
		return Integer.parseInt(params.get("targetPort"));
	}

	public static String getBalancerParam(Map<String, String> params) {
		String balancerName = params.get("balancer");
		if (balancerName == null)
			balancerName = Balancer.DEFAULT_NAME;
		return balancerName;
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

	public boolean isReadOnly() {
		return readOnly;
	}
	
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public Response createReadOnlyErrorResponse() {
		return Response.forbidden("The admin console is configured to be readOnly.").build();
	}

}
