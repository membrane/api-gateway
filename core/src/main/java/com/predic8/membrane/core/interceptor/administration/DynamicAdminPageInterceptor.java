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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.administration.AdminPageBuilder.*;
import static com.predic8.membrane.core.interceptor.rest.RESTInterceptor.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static com.predic8.membrane.core.util.URLParamUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * Handles the dynamic part of the admin console (= requests starting with "/admin/").
 */
public class DynamicAdminPageInterceptor extends AbstractInterceptor {
	private static final Logger log = LoggerFactory.getLogger(DynamicAdminPageInterceptor.class.getName());
	private boolean readOnly;
	private boolean useXForwardedForAsClientAddr;

	@Override
	public Outcome handleRequest(Exchange exc) {
		log.debug("request: {}", exc.getOriginalRequestUri());

		exc.setTimeReqSent(System.currentTimeMillis());

        Outcome o;
        try {
            o = dispatchRequest(exc);
        } catch (Exception e) {
			log.error("", e);
			internal(router.isProduction(),getDisplayName())
					.detail("Error in dynamic administration request!")
					.exception(e)
					.buildAndSetResponse(exc);
			return ABORT;
        }

        exc.setReceived();
		exc.setTimeResReceived(System.currentTimeMillis());

		return o;
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/?(\\?.*)?")
	public Response handleHomeRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/proxy/?(\\?.*)?")
	public Response handleProxyRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/service-proxy/show/?(\\?.*)?")
	public Response handleServiceProxyShowRequest(final Map<String, String> params, final String relativeRootPath) {
		final StringWriter writer = new StringWriter();

		final AbstractServiceProxy rule = (AbstractServiceProxy) RuleUtil.findRuleByIdentifier(router,params.get("name"));

		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_SERVICE_PROXIES;
			}

			@Override
			protected String getTitle() {
				return super.getTitle()+" "+rule.toString()+" ServiceProxy";
			}

			@Override
			protected void createTabContent() {
				h1().text(rule.toString()+" ServiceProxy").end();
				script().raw("""
						$(function() {
							$( "#subtab" ).tabs();
						});""").end();

				div().id("subtab");
				ul();
				li().a().href("#tab1").text("Visualization").end(2);
				li().a().href("#tab2").text("Statistics").end(2);
				//li().a().href("#tab3").text("XML Configuration").end(2);
				end();
				div().id("tab1");
				createServiceProxyVisualization(rule, relativeRootPath);
				end();
				div().id("tab2");
				createStatusCodesTable(rule.getStatisticCollector().getStatisticsByStatusCodes());
				br();
				createButton("View Messages", createQueryString("proxy", rule.toString()));
				end();
				end();
			}
		}.createPage());
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/proxy/show/?(\\?.*)?")
	public Response handlePruleShowRequest(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();

		final ProxyRule rule = (ProxyRule) RuleUtil.findRuleByIdentifier(router,params.get("name"));

		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_PROXIES;
			}

			@Override
			protected String getTitle() {
				return super.getTitle()+" "+rule.toString()+" Proxy";
			}

			@Override
			protected void createTabContent() {
				h1().text(rule.toString()+" Proxy").end();
				if (rule.getKey().getPort() != -1) {
					table();
					createTr("Listen Port",""+rule.getKey().getPort());
					end();
				}
				h2().text("Status Codes").end();
				createStatusCodesTable(rule.getStatisticCollector().getStatisticsByStatusCodes());
				h2().text("Interceptors").end();
				createInterceptorTable(rule.getInterceptors());
			}

		}.createPage());
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/service-proxy/save/?(\\?.*)?")
	public Response handleServiceProxySaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();

		logAddFwdRuleParams(params);

		ServiceProxy r = new ServiceProxy(new ServiceProxyKey("*",
				params.get("method"), ".*", getPortParam(params), null),
				params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		try {
			router.getRuleManager().addProxyAndOpenPortIfNew(r);
		} catch (PortOccupiedException e) {
			return Response.internalServerError(
					"The port could not be opened: Either it is occupied or Membrane does " +
					"not have enough privileges to do so.").build();
		}

		return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/proxy/save/?(\\?.*)?")
	public Response handleProxySaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();

		log.debug("adding proxy rule");
		log.debug("name: {}", params.get("name"));
		log.debug("port: {}", params.get("port"));

		ProxyRule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port")), null));
		r.setName(params.get("name"));
		router.getRuleManager().addProxyAndOpenPortIfNew(r);
		return respond(getProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/service-proxy/delete/?(\\?.*)?")
	public Response handleServiceProxyDeleteRequest(Map<String, String> params, String relativeRootPath) {
		if (readOnly)
			return createReadOnlyErrorResponse();

		Proxy proxy = RuleUtil.findRuleByIdentifier(router, params.get("name"));
		if (proxy != null)
			router.getRuleManager().removeRule(proxy);
		return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/service-proxy/start/?(\\?.*)?")
	public Response handleServiceProxyStartRequest(Map<String, String> params, String relativeRootPath)
			throws Exception {
		if (readOnly)
			return createReadOnlyErrorResponse();

		Proxy proxy = RuleUtil.findRuleByIdentifier(router, params.get("name"));
		Proxy newProxy = proxy.clone();
		router.getRuleManager().replaceRule(proxy, newProxy);
		return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/proxy/delete/?(\\?.*)?")
	public Response handleProxyDeleteRequest(Map<String, String> params, String relativeRootPath) {
		if (readOnly)
			return createReadOnlyErrorResponse();

		router.getRuleManager().removeRule(
				RuleUtil.findRuleByIdentifier(router, params.get("name")));
		return respond(getProxyPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/transport/?(\\?.*)?")
	public Response handleTransportRequest(Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_TRANSPORT;
			}

			@Override
			protected void createTabContent() {
				h2().text("Transport").end();

				h3().text("Transport Interceptors").end();
				createInterceptorTable(router.getTransport().getInterceptors());
			}

		}.createPage());
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/system/?(\\?.*)?")
	public Response handleSystemRequest(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {

			static final int mb = 1024*1024;
			final DecimalFormat formatter = new DecimalFormat("#.00");

			private String formatMemoryValue(float value){
				float newValue = value / (float)mb;

				String unit = "MB";
				if(newValue > 1024)
					unit = "GB";

				return formatter.format(newValue) + " " + unit;
			}

			@Override
			protected int getSelectedTab() {
				return TAB_ID_SYSTEM;
			}

			@Override
			protected void createTabContent() {
				h2().text("System").end();

				p().text("Availabe system memory: " + formatMemoryValue(Runtime.getRuntime().totalMemory())).end();
				p().text("Free system memory: " + formatMemoryValue(Runtime.getRuntime().freeMemory())).end();

				p().text("Membrane version: " + Constants.VERSION).end();
			}
		}.createPage());
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/balancers/?(\\?.*)?")
	public Response handleBalancersRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getBalancersPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/clusters/?(\\?.*)?")
	public Response handleClustersRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getClustersPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/clusters/show/?(\\?.*)?")
	public Response handleClustersShowRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getClusterPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/clusters/save/?(\\?.*)?")
	public Response handleClustersSaveRequest(Map<String, String> params, String relativeRootPath) {
		if (readOnly)
			return createReadOnlyErrorResponse();

		log.debug("adding cluster");
		log.debug("balancer: {}", getBalancerParam(params));
		log.debug("name: {}", params.get("name"));

		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).addCluster(params.get("name"));

		return respond(getClustersPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/show/?(\\?.*)?")
	public Response handleNodeShowRequest(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_LOAD_BALANCING;
			}

			@Override
			protected void createTabContent() {
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

	@SuppressWarnings("unused")
	@Mapping("/admin/node/sessions/?(\\?.*)?")
	public Response handleNodeSessionsRequest(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_LOAD_BALANCING;
			}

			@Override
			protected void createTabContent() {
				h2().text("Node " + params.get("host")+":"+params.get("port")).end();
				h3().text("Sessions").end();
				createSessionsTable( BalancerUtil.lookupBalancer(router, getBalancerParam(params)).getSessionsByNode(
						params.get("cluster"),
						new Node(params.get("host"), Integer.parseInt(params.get("port")))));
			}

		}.createPage());
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/save/?(\\?.*)?")
	public Response handleNodeSaveRequest(Map<String, String> params, String relativeRootPath) {
		if (readOnly)
			return createReadOnlyErrorResponse();

		log.debug("adding node");
		log.debug("balancer: {}", getBalancerParam(params));
		log.debug("cluster: {}", params.get("cluster"));
		log.debug("host: {}", params.get("host"));
		log.debug("port: {}", params.get("port"));

		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).up(
				params.get("cluster"),
				params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters", createQueryString("balancer", getBalancerParam(params), "cluster", params.get("cluster")), relativeRootPath);
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/up/?(\\?.*)?")
	public Response handleNodeUpRequest(Map<String, String> params, String relativeRootPath) {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).up(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters", createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/takeout/?(\\?.*)?")
	public Response handleNodeTakeoutRequest(Map<String, String> params, String relativeRootPath) {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).takeout(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters", createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/down/?(\\?.*)?")
	public Response handleNodeDownRequest(Map<String, String> params, String relativeRootPath) {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).down(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters", createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/delete/?(\\?.*)?")
	public Response handleNodeDeleteRequest(Map<String, String> params, String relativeRootPath) {
		if (readOnly)
			return createReadOnlyErrorResponse();

		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).removeNode(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port")));
		return redirect("clusters", createQueryString("balancer", getBalancerParam(params), "cluster",params.get("cluster")), relativeRootPath);
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/node/reset/?(\\?.*)?")
	public Response handleNodeResetRequest(Map<String, String> params, String relativeRootPath) {
		BalancerUtil.lookupBalancer(router, getBalancerParam(params)).getNode(
				params.get("cluster"), params.get("host"),
				Integer.parseInt(params.get("port"))).clearCounter();
		return redirect("node", createQueryString("balancer", getBalancerParam(params),
				"cluster",params.get("cluster"),
				"host",params.get("host"),
				"port",params.get("port")), relativeRootPath);
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/statistics")
	public Response handleStatisticsRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getStatisticsPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/streams")
	public Response handleStreamPumpsRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getStreamPumpsPage(params, relativeRootPath));
	}

	@Mapping("/admin/calls(/?\\?.*)?")
	public Response handleCallsRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getCallsPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/call/?(\\?.*)?")
	public Response handleCallRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getCallPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/clients")
	public Response handleClientsRequest(Map<String, String> params, String relativeRootPath) {
		return respond(getClientsPage(params, relativeRootPath));
	}

	@SuppressWarnings("unused")
	@Mapping("/admin/about")
	public Response getAbout(Map<String, String> params, String relativeRootPath) {
		return respond(getAboutPage(params, relativeRootPath));
	}

	private String getServiceProxyPage(Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_SERVICE_PROXIES;
			}

			@Override
			protected void createTabContent() {
				h3().text("ServiceProxies").end();
				createFwdRulesTable();
				createAddFwdRuleForm();
			}

		}.createPage();
	}

	private String getProxyPage(Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_PROXIES;
			}

			@Override
			protected void createTabContent() {
				h3().text("Proxies").end();
				createProxyRulesTable();
				createAddProxyRuleForm();
			}

		}.createPage();
	}

	private String getClusterPage(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_LOAD_BALANCING;
			}

			@Override
			protected void createTabContent() {
				String balancer = getBalancerParam(params);
				h2().text("Cluster " + params.get("cluster") + " of Balancer " + balancer).end();
				createNodesTable(balancer);
				createAddNodeForm(balancer);
			}

		}.createPage();
	}

	private String getClustersPage(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {

			@Override
			protected int getSelectedTab() {
				return TAB_ID_LOAD_BALANCING;
			}

			@Override
			protected void createTabContent() {
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

	private String getBalancersPage(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {

			@Override
			protected int getSelectedTab() {
				return TAB_ID_LOAD_BALANCING;
			}

			@Override
			protected void createTabContent() {
				h1().text("Balancers").end();
				createBalancersTable();
			}

		}.createPage();
	}

	private String getStatisticsPage(Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_STATISTICS;
			}

			@Override
			protected void createTabContent() {
				h3().text("Statistics").end();
				createStatisticsTable();
			}

		}.createPage();
	}

	private String getStreamPumpsPage(Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_STREAM_PUMPS;
			}
			@Override
			protected void createTabContent() {
				h3().text("Stream Pump Statistics").end();
				createStreamPumpsTable();
			}
		}.createPage();
	}

	private String getCallsPage(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_CALLS;
			}

			@Override
			protected void createTabContent() {
				PropertyValueCollector propertyValues = new PropertyValueCollector();
				propertyValues.setUseXForwardedForAsClientAddr(useXForwardedForAsClientAddr);

				router.getExchangeStore().collect(propertyValues);
				h3().text("Filter").end();
				form();
				div();
				span().text("Method").end()
				.select().id("message-filter-method")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", true);
				for (String s : sort(propertyValues.getMethods())) {
					option(s, s, false);
				}
				end();
				span().text("Status Code").end()
				.select().id("message-filter-statuscode")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", true);
				for (Integer i : sort(propertyValues.statusCodes)) {
					option(i.toString(), i.toString(), false);
				}
				end(2);
				div();
				span().text("Proxy").end()
				.select().id("message-filter-proxy")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", !params.containsKey("proxy"));
				for (String s : sort(propertyValues.getProxies())) {
					option(s, s, s.equals(params.get("proxy")));
				}
				end();
				span().text("Client").end()
				.select().id("message-filter-client")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", !params.containsKey("client"));
				for (String s : sort(propertyValues.getClients())) {
					option(s, s, s.equals(params.get("client")));
				}
				end();
				span().text("Server").end()
				.select().id("message-filter-server")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", true);
				for (String s : sort(propertyValues.getServers())) {
					option(s==null?"undefined":s, s==null?"":s, false);
				}
				end(2);
				div();
				span().text("Request Content-Type").end()
				.select().id("message-filter-reqcontenttype")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", true);
				for (String s : sort(propertyValues.getReqContentTypes())) {
					option(s.isEmpty()?"undefined":s, s, false);
				}
				end();
				span().text("Response Content-Type").end()
				.select().id("message-filter-respcontenttype")
				.onchange("membrane.onFilterUpdate();");
				option("*", "*", true);
				for (String s : sort(propertyValues.getRespContentTypes())) {
					option(s.isEmpty()?"undefined":s, s, false);
				}
				end(2);
				span().text("Text Search").end()
				.input().type("text").id("message-filter-search").name("message-filter-search").onkeydown("membrane.onFilterUpdate();").onchange("membrane.onFilterUpdate();").end(2);
				end();
				br();
				createButton("Reset Filter", null);
				a().id("reload-data-button").classAttr("mb-button").text("Reload data").end();
				label().forAttr("reload-data-checkbox").checkbox().checked("true").id("reload-data-checkbox").text("Auto Reload").end();
				addMessageText();
				createMessageStatisticsTable();
			}

			private void addMessageText() {
				if(!(router.getExchangeStore() instanceof LimitedMemoryExchangeStore))
					h3().text("Messages").end();
				if (router.getExchangeStore() instanceof LimitedMemoryExchangeStore) {
					h3().text(getLimitedMemoryExchangeStoreMessageText()).a().href("https://www.membrane-soa.org/service-proxy-doc/current/configuration/reference/limitedMemoryExchangeStore.htm").text("(What is this?)").end(2);
				}
			}

			private String getLimitedMemoryExchangeStoreMessageText() {

					LimitedMemoryExchangeStore lmes = (LimitedMemoryExchangeStore)router.getExchangeStore();
					float usage = 100.0f * lmes.getCurrentSize() / lmes.getMaxSize();
					Long oldestTimeResSent = lmes.getOldestTimeResSent();
					String usageStr =
							oldestTimeResSent == null ? "" :
								String.format("; usage %.0f%%; the last %s", usage,
										DateAndTimeUtil.prettyPrintTimeSpan(System.currentTimeMillis() - oldestTimeResSent));
					return "Messages" + String.format(
							" (limited to last %.2f MB%s)",
							lmes.getMaxSize()/1000000.,
							usageStr);
			}

		}.createPage();
	}

	private <T extends Comparable<? super T>> List<T> sort(Set<T> data) {
		int nulls = 0;
		ArrayList<T> res = new ArrayList<>(data.size());
		for (T t : data)
			if (t == null)
				nulls++;
			else
				res.add(t);
		Collections.sort(res);
		while (nulls-- > 0)
			res.addFirst(null);
		return res;
	}

	private String getCallPage(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_CALLS;
			}

			@Override
			protected void createTabContent() {
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
				div().classAttr("proxy-config").pre().id("request-raw").end().end();
				end();
				div().id("requestHeadersTab");
				creatHeaderTable("request-headers");
				end();
				div().id("requestBodyTab");
				div().classAttr("proxy-config").pre().id("request-body").end().end();
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
				div().classAttr("proxy-config").pre().id("response-raw").end().end();
				end();
				div().id("responseHeadersTab");
				creatHeaderTable("response-headers");
				end();
				div().id("responseBodyTab");
				div().classAttr("proxy-config").pre().id("response-body").end().end();
				end();
				end();
				end();
				end();
			}

		}.createPage();
	}

	private String getClientsPage(Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_CLIENTS;
			}

			@Override
			protected void createTabContent() {
				h3().text("Statistics").end();
				createClientsStatisticsTable();
			}

		}.createPage();
	}

	private String getAboutPage(final Map<String, String> params, String relativeRootPath) {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params, readOnly) {
			@Override
			protected int getSelectedTab() {
				return TAB_ID_ABOUT;
			}

			@Override
			protected void createTabContent() {
				h3().text("Impressum").end();
				p().text("predic8 GmbH").br().text("Koblenzer Str. 65").br().br().text("53173 Bonn").end();
			}

		}.createPage();
	}

	private Outcome dispatchRequest(Exchange exc) throws URISyntaxException, IOException, InvocationTargetException, IllegalAccessException {
		String pathQuery = URLUtil.getPathQuery(router.getUriFactory(), exc.getDestinations().getFirst());
		for (Method m : getClass().getMethods() ) {
			Mapping a = m.getAnnotation(Mapping.class);
			if ( a != null && Pattern.matches(a.value(), pathQuery)) {
				exc.setResponse((Response)m.invoke(this, new Object[] { getParams(exc), getRelativeRootPath(pathQuery) }));
				return RETURN;
			}
		}
		return CONTINUE;
	}

	private Map<String, String> getParams(Exchange exc) throws URISyntaxException, IOException {
		return URLParamUtil.getParams(router.getUriFactory(), exc, ERROR);
	}

	private Response respond(String page) {
		return Response.ok().contentType(TEXT_HTML_UTF8).body(page.getBytes(UTF_8)).build();
	}

	private Response redirect(String ctrl, String query, String relativeRootPath) {
		return Response.found(relativeRootPath + createHRef(ctrl, "show", query)).build();
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
			return Balancer.DEFAULT_NAME;
		return balancerName;
	}

	private void logAddFwdRuleParams(Map<String, String> params) {
		log.debug("adding fwd rule");
		log.debug("name: {}", params.get("name"));
		log.debug("port: {}", params.get("port"));
		log.debug("client host: {}", params.get("clientHost"));
		log.debug("method: {}", params.get("method"));
		log.debug("path: {}", params.get("path"));
		log.debug("target host: {}", params.get("targetHost"));
		log.debug("target port: {}", params.get("targetPort"));
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public void setUseXForwardedForAsClientAddr(boolean useXForwardedForAsClientAddr) {
		this.useXForwardedForAsClientAddr = useXForwardedForAsClientAddr;
	}

	public Response createReadOnlyErrorResponse() {
		return Response.forbidden("The admin console is configured to be readOnly.").build();
	}
}
