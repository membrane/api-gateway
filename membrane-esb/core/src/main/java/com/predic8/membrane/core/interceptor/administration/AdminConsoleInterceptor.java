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

import static com.predic8.membrane.core.util.HttpUtil.createResponse;
import static com.predic8.membrane.core.util.URLParamUtil.createQueryString;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.exchange.Exchange;
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
		StringWriter writer = new StringWriter();
		
		final ServiceProxy rule = (ServiceProxy) RuleUtil.findRuleByIdentifier(router,params.get("name"));
		
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
				h2().text("Status Codes").end();
				createStatusCodesTable(rule.getStatisticsByStatusCodes());
				h2().text("Configuration").end();
				h3().text("Visualization").end();
				createServiceProxyVisualization(rule, relativeRootPath);
				h3().text("XML").end();
				String xml = "";
				try {
					xml = rule.toXml();
					xml = TextUtil.formatXML(new StringReader(xml));
					pre().text(xml).end();
				} catch (Exception e) {
					log.error(xml);
					e.printStackTrace();
				}
			}
		}.createPage());
	}

	@Mapping("/admin/proxy/show/?(\\?.*)?")
	public Response handlePruleShowRequest(final Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();

		final ProxyRule rule = (ProxyRule) RuleUtil.findRuleByIdentifier(router,params.get("name"));
		
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
	public Response handleFruleSaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		logAddFwdRuleParams(params);
		
		Rule r = new ServiceProxy(new ServiceProxyKey("*",
				params.get("method"), ".*", getPortParam(params)),
				params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		router.getRuleManager().addProxyIfNew(r);
		
		return respond(getServiceProxyPage(params, relativeRootPath));
	}
	
	@Mapping("/admin/proxy/save/?(\\?.*)?")
	public Response handlePruleSaveRequest(Map<String, String> params, String relativeRootPath) throws Exception {
		log.debug("adding proxy rule");
		log.debug("name: " + params.get("name"));
		log.debug("port: " + params.get("port"));
		
		Rule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port"))));
		r.setName(params.get("name"));
		router.getRuleManager().addProxyIfNew(r);
		return respond(getProxyPage(params, relativeRootPath));
	}

	@Mapping("/admin/service-proxy/delete/?(\\?.*)?")
	public Response handleServiceProxyDeleteRequest(Map<String, String> params, String relativeRootPath)
	  throws Exception {
			router.getRuleManager().removeRule(
					RuleUtil.findRuleByIdentifier(router, params.get("name")));
			return respond(getServiceProxyPage(params, relativeRootPath));
	}

	@Mapping("/admin/proxy/delete/?(\\?.*)?")
	public Response handleProxyDeleteRequest(Map<String, String> params, String relativeRootPath)
	  throws Exception {
			router.getRuleManager().removeRule(
					RuleUtil.findRuleByIdentifier(router, params.get("name")));
			return respond(getProxyPage(params, relativeRootPath));
	}
	
	@Mapping("/admin/transport/?(\\?.*)?")
	public Response handleTransportRequest(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		return respond(new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
	
	private String getServiceProxyPage(Map<String, String> params, String relativeRootPath)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		return new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		return new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		return new AdminPageBuilder(writer, router, relativeRootPath, params) {
		
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
		return new AdminPageBuilder(writer, router, relativeRootPath, params) {
				
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
		return new AdminPageBuilder(writer, router, relativeRootPath, params) {
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
		String pathQuery = URIUtil.getPathQuery(exc.getDestinations().get(0));
		for (Method m : getClass().getMethods() ) {
			Mapping a = m.getAnnotation(Mapping.class);
			if ( a != null && Pattern.matches(a.value(), pathQuery)) {
				exc.setResponse((Response)m.invoke(this, new Object[] { getParams(exc), getRelativeRootPath(pathQuery) }));
				return Outcome.ABORT;
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
	
	public static String getBalancerParam(Map<String, String> params) {
		String balancerName = params.get("balancer");
		if (balancerName == null)
			balancerName = Balancer.DEFAULT_NAME;
		return balancerName;
	}

}
