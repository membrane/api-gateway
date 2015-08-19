
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

import static com.predic8.membrane.core.util.URLParamUtil.createQueryString;
import static org.apache.commons.lang.time.DurationFormatUtils.formatDurationHMS;
import static org.apache.log4j.Level.ALL;
import static org.apache.log4j.Level.DEBUG;
import static org.apache.log4j.Level.ERROR;
import static org.apache.log4j.Level.FATAL;
import static org.apache.log4j.Level.INFO;
import static org.apache.log4j.Level.OFF;
import static org.apache.log4j.Level.TRACE;
import static org.apache.log4j.Level.WARN;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.Cluster;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.interceptor.balancer.Session;
import com.predic8.membrane.core.interceptor.flow.RequestInterceptor;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.rules.AbstractProxy;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.http.StreamPump;
import com.predic8.membrane.core.util.TextUtil;

public class AdminPageBuilder extends Html {

	static final int TAB_ID_SERVICE_PROXIES = 0;
	static final int TAB_ID_PROXIES = 1;
	static final int TAB_ID_TRANSPORT = 2;
	static final int TAB_ID_SYSTEM = 3;
	static final int TAB_ID_LOAD_BALANCING = 4;
	static final int TAB_ID_STATISTICS = 5;
	static final int TAB_ID_CALLS = 6;
	static final int TAB_ID_STREAM_PUMPS = 7;
	static final int TAB_ID_CLIENTS = 8;
	static final int TAB_ID_ABOUT = 9;

	private final Router router;
	private final Map<String, String> params;
	private final StringWriter writer;
	private final String relativeRootPath;
	private final boolean readOnly;

	static public String createHRef(String ctrl, String action, String query) {
		return "/admin/"+ctrl+(action!=null?"/"+action:"")+(query!=null?"?"+query:"");
	}

	public AdminPageBuilder(StringWriter writer, Router router, String relativeRootPath, Map<String, String> params, boolean readOnly) {
		super(writer);
		this.router = router;
		this.params = params;
		this.writer = writer;
		this.relativeRootPath = relativeRootPath;
		this.readOnly = readOnly;
	}

	private String makeRelative(String path) {
		if (path.startsWith("/"))
			return relativeRootPath + path;
		return path;
	}

	@Override
	public Html action(String value) {
		return super.action(makeRelative(value));
	}

	@Override
	public Html src(String value) {
		return super.src(makeRelative(value));
	}

	@Override
	public Html href(String value) {
		return super.href(makeRelative(value));
	}

	public String createPage() throws Exception {
		html();
		createHead();
		body();
		div().id("tabs").classAttr("ui-tabs ui-widget ui-widget-content ui-corner-all");
		createTabs(getSelectedTab());
		div().classAttr("ui-tabs-panel ui-widget-content ui-corner-bottom");
		createTabContent();
		end();
		p().classAttr("footer").raw(Constants.HTML_FOOTER).end();
		end();
		endAll();
		done();
		return writer.getBuffer().toString();
	}

	protected String getTitle() {
		return Constants.PRODUCT_NAME + " Administration";
	}

	protected int getSelectedTab() {
		return 0;
	}

	protected void createTabContent() throws Exception {
	}


	protected void createHead() {
		head();
		title().text(getTitle()).end();
		style().attr("type", "text/css").text("@import '" + relativeRootPath + "/admin/datatables/css/demo_table_jui.css';\n" +
				"@import '" + relativeRootPath + "/admin/jquery-ui/css/custom-theme/jquery-ui-1.8.13.custom.css';"+
				"@import '" + relativeRootPath + "/admin/css/membrane.css';").end();
		link().rel("stylesheet").href("/admin/formValidator/validationEngine.jquery.css").type("text/css");
		script().attr("type","text/javascript").src("/admin/jquery/jquery-1.6.1.js").end();
		script().attr("type","text/javascript").src("/admin/datatables/js/jquery.dataTables.min.js").end();
		script().attr("type","text/javascript").src("/admin/jquery-ui/js/jquery-ui-1.8.13.custom.min.js").end();
		script().attr("type","text/javascript").src("/admin/formValidator/jquery.validationEngine-en.js").end();
		script().attr("type","text/javascript").src("/admin/formValidator/jquery.validationEngine.js").end();
		script().attr("type","text/javascript").raw("var relativeRootPath=\"" + StringEscapeUtils.escapeJavaScript(relativeRootPath) + "\";").end();
		script().attr("type","text/javascript").src("/admin/js/membrane.js").end();
		createMetaElements();
		end();
	}

	protected void createMetaElements() {
		meta().attr("http-equiv", "X-UA-Compatible", "content", "IE=Edge").end();
		if (params.containsKey("refresh")) {
			try {
				createMeta("refresh", "" + Integer.parseInt(params.get("refresh")));
			} catch (NumberFormatException e) {
				// do nothing
			}
		}
	}

	protected void createMeta(String... meta) {
		for (int i = 0; i< meta.length; i+=2) {
			meta().httpEquiv(meta[i]).content(meta[i+1]).end();
		}
	}

	protected void createInterceptorTable(List<Interceptor> interceptors) {
		table().id("interceptortable").attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display", "id", "interceptor-table");
		thead();
		tr();
		createThs("Order", "Name");
		end();
		end();
		tbody();
		int j = 0;
		for (Interceptor i : interceptors) {
			tr();
			createTds("" + ++j, i.getDisplayName());
			end();
		}
		end();
		end();
	}

	protected void createAddFwdRuleForm() {
		if (readOnly)
			return;
		if (!router.getTransport().isOpeningPorts())
			/*
			 * Of course, he could just hide the "port" field here, but as
			 * dynamically adding proxies without setting the @path is quite
			 * pointless with web application deployment, we just disable that
			 * feature.
			 */
			return;

		form().id("addFwdRuleForm").action("/admin/service-proxy/save").method("POST");
		div()
		.span().text("Name").end()
		.span().input().type("text").id("name").name("name").classAttr("validate[required]").end(2)

		.span().text("Listen Port").end()
		.span().input().type("text").id("port").name("port").size("5").classAttr("validate[required,custom[integer]]").end(2)

		.span().text("Method").end()
		.span().select().id("method").name("method")
		.option().text("*").end()
		.option().text("GET").end()
		.option().text("POST").end()
		.option().text("DELETE").end()
		.end(2)

		.span().text("Target Host").end()
		.span().input().type("text").id("targetHost").name("targetHost").classAttr("validate[required]").end(2)

		.span().text("Target Port").end()
		.span().input().type("text").id("targetPort").name("targetPort").size("5").classAttr("validate[required,custom[integer]]").end(2)

		.span().input().value("Add").type("submit").classAttr("mb-button").end(2);
		end();
		end();
	}

	protected void createAddProxyRuleForm() {
		if (readOnly)
			return;

		form().id("addProxyRuleForm").action("/admin/proxy/save").method("POST");
		div()
		.span().text("Name").end()
		.span().input().type("text").id("p-name").name("name").classAttr("validate[required]").end(2) //id != name so that validation error reports on the page show of at the right places.
		.span().text("Listen Port").end()
		.span().input().type("text").id("p-port").name("port").size("5").classAttr("validate[required,custom[integer]]").end(2)
		.span().input().value("Add").type("submit").classAttr("mb-button").end(2);
		end();
		end();
	}

	protected void createFwdRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display", "id", "fwdrules-table");
		thead();
		tr();
		createThs("Order", "Name", "Listen Port", "Virtual Host", "Method","Path","Target Host","Target Port","Count","Actions");
		end();
		end();
		tbody();
		end();
		end();
	}

	protected void createProxyRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display", "id", "proxy-rules-table");
		thead();
		tr();
		createThs("Name", "Listen Port", "Count", "Actions");
		end();
		end();
		tbody();
		for (ProxyRule rule : getProxyRules()) {
			tr();
			td();
			createLink(rule.toString(), "proxy", "show", createQueryString("name",RuleUtil.getRuleIdentifier(rule)));
			end();
			createTds(rule.getKey().getPort() == -1 ? "" : ""+rule.getKey().getPort(),
					""+rule.getCount());
			if (!readOnly)
				td().a().href("/admin/proxy/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).span().classAttr("ui-icon ui-icon-trash").end(3);
			end();
		}
		end();
		end();
	}

	protected void createTabs(int selected) throws Exception {
		ul().classAttr("ui-tabs-nav ui-helper-reset ui-helper-clearfix ui-widget-header ui-corner-all");
		li().classAttr(getSelectedTabStyle(TAB_ID_SERVICE_PROXIES, selected));
		a().href("/admin").text("ServiceProxies").end();
		end();
		li().classAttr(getSelectedTabStyle(TAB_ID_PROXIES, selected));
		createLink("Proxies", "proxy", null, null);
		end();
		li().classAttr(getSelectedTabStyle(TAB_ID_TRANSPORT, selected));
		createLink("Transport", "transport", null, null);
		end();
		li().classAttr(getSelectedTabStyle(TAB_ID_SYSTEM, selected));
		createLink("System", "system", null, null);
		end();
		if (BalancerUtil.hasLoadBalancing(router)) {
			li().classAttr(getSelectedTabStyle(TAB_ID_LOAD_BALANCING, selected));
			createLink("Load Balancing", "balancers", null, null);
			end();
		}
		li().classAttr(getSelectedTabStyle(TAB_ID_STATISTICS, selected));
		createLink("Statistics", "statistics", null, null);
		end();
		li().classAttr(getSelectedTabStyle(TAB_ID_CALLS, selected));
		createLink("Calls", "calls", null, null);
		end();
		li().classAttr(getSelectedTabStyle(TAB_ID_STREAM_PUMPS, selected));
		createLink("Stream Pumps", "streams", null, null);
		end();
		li().classAttr(getSelectedTabStyle(TAB_ID_CLIENTS, selected));
		createLink("Clients", "clients", null, null);
		end();
		li().style("float: right;").classAttr(getSelectedTabStyle(TAB_ID_ABOUT, selected));
		createLink("About", "about", null, null);
		end();
		end();
	}

	protected void createAddClusterForm(String balancerName) {
		if (readOnly)
			return;

		form().id("addClusterForm").action("/admin/clusters/save").method("POST");
		input().type("hidden").name("balancer").value(balancerName).end();
		div()
		.span().text("Name").end()
		.span().input().type("text").id("name").name("name").classAttr("validate[required]").end(2)
		.span().input().value("Add Cluster").type("submit").classAttr("mb-button").end(2);
		end();
		end();
	}

	protected void createBalancersTable()
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display balancersTable");
		thead();
		tr();
		createThs("Name", "Failover", "Health");
		end();
		end();
		tbody();
		for (LoadBalancingInterceptor loadBalancingInterceptor : BalancerUtil.collectBalancers(router)) {
			tr();
			td();
			createLink(loadBalancingInterceptor.getName(), "clusters", null, createQueryString("balancer", loadBalancingInterceptor.getName()));
			end();
			createTds(
					loadBalancingInterceptor.isFailOver() ? "yes" : "no",
							getFormatedHealth(loadBalancingInterceptor.getName()));

			end();
		}
		end();
		end();
	}

	protected void createClustersTable(String balancerName)
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display clustersTable");
		thead();
		tr();
		createThs("Name", "#Nodes", "Health");
		end();
		end();
		tbody();
		for (Cluster c : BalancerUtil.lookupBalancer(router, balancerName).getClusters()) {
			tr();
			td();
			createLink(c.getName(), "clusters", "show", createQueryString("balancer", balancerName, "cluster", c.getName()));
			end();

			createTds(String.valueOf(BalancerUtil.lookupBalancer(router, balancerName).
					getAllNodesByCluster(c.getName()).size()),
					getFormatedHealth(balancerName, c.getName()));
			end();
		}
		end();
		end();
	}

	protected void createAddNodeForm(String balancerName) {
		if (readOnly)
			return;

		form().id("addNodeForm").action("/admin/node/save").method("POST");
		input().type("hidden").name("balancer").value(balancerName).end();
		input().type("hidden").name("cluster").value(params.get("cluster")).end();
		div()
		.span().text("Host").end()
		.span().input().type("text").id("host").name("host").classAttr("validate[required]").end(2)
		.span().text("Port").end()
		.span().input().type("text").id("port").name("port").size("5").classAttr("validate[required,custom[integer]]").end(2)
		.span().input().value("Add Node").type("submit").classAttr("mb-button").end(2);
		end();
		end();
	}

	protected void createSessionsTable(List<Session> sessions) {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display sessionsTable");
		thead();
		tr();
		createThs("Id", "Last Used");
		end();
		end();
		tbody();
		for (Session s : sessions ) {
			tr();
			createTds(s.getId(), formatDurationHMS(System.currentTimeMillis()-s.getLastUsed()));
			end();
		}
		end();
		end();
	}

	protected void createStatusCodesTable(Map<Integer, StatisticCollector> statusCodes) throws Exception {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display", "id", "statuscode-table");
		thead();
		tr();
		createThs("Status Code", "Count", "Minimum Time", "Maximum Time", "Average Time",
				"Total Request Body Bytes", "Total Response Body Bytes");
		end();
		end();
		tbody();
		synchronized (statusCodes) {
			for (Map.Entry<Integer, StatisticCollector> codes : statusCodes.entrySet() ) {
				StatisticCollector statisticCollector = codes.getValue();
				tr().style("text-align: right;");
				td().style("text-align:left;").text(""+codes.getKey()).end();
				createTds(
						""+statisticCollector.getCount(),
						""+statisticCollector.getMinTime(),
						""+statisticCollector.getMaxTime(),
						""+statisticCollector.getAvgTime(),
						""+statisticCollector.getBytesSent(),
						""+statisticCollector.getBytesReceived());
				end();
			}
		}
		end();
		end();
	}

	protected void createStatisticsTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display", "id", "statistics-table");
		thead();
		tr();
		createThs("Name", "Count", "Minimum Time", "Maximum Time", "Average Time",
				"Total Request Body Bytes", "Total Response Body Bytes");
		end();
		end();
		tbody();
		for (Map.Entry<String, StatisticCollector> entry : getStatistics().entrySet()) {
			StatisticCollector statisticCollector = entry.getValue();
			tr().style("text-align: right;");
			td().style("text-align:left;").text(entry.getKey()).end();
			createTds(
					""+statisticCollector.getCount(),
					""+statisticCollector.getMinTime(),
					""+statisticCollector.getMaxTime(),
					""+statisticCollector.getAvgTime(),
					""+statisticCollector.getBytesSent(),
					""+statisticCollector.getBytesReceived());
			end();
		}
		end();
		end();
	}

	protected void createStreamPumpsTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display", "id", "stream-pumps-table");
		thead();
		tr();
		createThs("Name", "Service Proxy", "Creation Time", "Active Time", "Transferred Bytes");
		end();
		end();
		tbody();
		for (StreamPump p : router.getStatistics().getStreamPumpStats().getStreamPumps()) {
			tr().style("text-align: right;");
			td().style("text-align:left;").text(p.getName()).end();
			createTds(
					p.getServiceProxyName(),
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(p.getCreationTime()),
					(System.currentTimeMillis() - p.getCreationTime())/1000 + " seconds", // TODO: Pretty Print with library
					""+p.getTransferredBytes()
					);
			end();
		}
		end();
		end();
	}


	protected void createNodesTable(String balancerName) throws Exception {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display nodesTable");
		thead();
		tr();
		createThs("Node", "Status", "Count", "Errors", "Time since last up", "Sessions", "Current Threads", "Action");
		end();
		end();
		tbody();
		for (Node n : BalancerUtil.lookupBalancer(router, balancerName).getAllNodesByCluster(params.get("cluster"))) {
			tr();
			td();
			createLink(""+n.getHost()+":"+n.getPort(), "node", "show",
					createQueryString("balancer", balancerName, "cluster", params.get("cluster"), "host", n.getHost(),"port", ""+n.getPort() ));
			end();
			createTds( getStatusString(n), ""+n.getCounter(),
					String.format("%1$.2f%%", n.getErrors()*100),
					formatDurationHMS(System.currentTimeMillis()-n.getLastUpTime()),
					""+BalancerUtil.lookupBalancer(router, balancerName).getSessionsByNode(params.get("cluster"),n).size(),
					""+n.getThreads());
			td();
			createIcon("ui-icon-eject", "node", "takeout", "takeout", createQuery4Node(n));
			createIcon("ui-icon-circle-arrow-n", "node", "up", "up", createQuery4Node(n));
			createIcon("ui-icon-circle-arrow-s", "node", "down", "down", createQuery4Node(n));
			if (!readOnly)
				createIcon("ui-icon-trash", "node", "delete", "delete", createQuery4Node(n));
			end();
			end();
		}
		end();
		end();
		script().raw("$(document).ready(function() { $('.nodesTable').dataTable({'bJQueryUI': true, \"bPaginate\": false}); } );").end();
	}

	private String getStatusString(Node n) {
		switch (n.getStatus()) {
		case TAKEOUT:
			return "In take out";
		default:
			return ""+n.getStatus();
		}
	}

	private String createQuery4Node(Node n) throws UnsupportedEncodingException {
		return createQueryString("balancer", DynamicAdminPageInterceptor.getBalancerParam(params),
				"cluster", params.get("cluster"),"host", n.getHost(), "port", ""+n.getPort());
	}

	private void createIcon(String icon, String ctrl, String action, String tooltip, String query) {
		a().href(createHRef(ctrl, action, query)).span().classAttr("ui-icon "+icon).style("float:left;").title(tooltip).end(2);
	}

	private String getFormatedHealth(String balancerName, String cluster) {
		return String.format("%d up/ %d down",
				BalancerUtil.lookupBalancer(router, balancerName).getAvailableNodesByCluster(cluster).size(),
				BalancerUtil.lookupBalancer(router, balancerName).getAllNodesByCluster(cluster).size() -
				BalancerUtil.lookupBalancer(router, balancerName).getAvailableNodesByCluster(cluster).size());
	}

	private String getFormatedHealth(String balancerName) {
		Balancer balancer = BalancerUtil.lookupBalancer(router, balancerName);
		int available = 0, all = 0;
		for (Cluster c : balancer.getClusters()) {
			all += balancer.getAllNodesByCluster(c.getName()).size();
			available += balancer.getAvailableNodesByCluster(c.getName()).size();
		}
		return String.format("%d up/ %d down", available, all - available);
	}

	private String getSelectedTabStyle(int ownPos, int selected) {
		return ownPos == selected
				? "ui-state-default ui-corner-top ui-tabs-selected ui-state-active"
						: "ui-state-default ui-corner-top";
	}

	private List<ProxyRule> getProxyRules() {
		List<ProxyRule> rules = new LinkedList<ProxyRule>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ProxyRule)) continue;
			rules.add((ProxyRule) r);
		}
		return rules;
	}

	private Map<String, StatisticCollector> getStatistics() {
		Map<String, StatisticCollector> res = new TreeMap<String, StatisticCollector>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof AbstractProxy)) continue;
			StatisticCollector sc = new StatisticCollector(true);
			for (StatisticCollector s : ((AbstractProxy) r).getStatisticsByStatusCodes().values())
				sc.collectFrom(s);
			res.put(r.getName(), sc);
		}
		return res;
	}

	protected void createButton(String label, String ctrl, String action, String query) throws UnsupportedEncodingException {
		a().classAttr("mb-button").href(createHRef(ctrl, action, query)).text(label).end();
	}

	protected void createLink(String label, String ctrl, String action, String query) throws UnsupportedEncodingException {
		a().href(createHRef(ctrl, action, query)).text(label).end();
	}

	protected void createThs(String... data) {
		for (String d : data) {
			th().text(d).end();
		}
	}

	protected void createTds(String... data) {
		for (String d : data) {
			td().text(d).end();
		}
	}

	protected void createTr(String... data) {
		tr();
		for (String d : data)
			td().text(d).end();
		end();
	}

	private void createInterceptorVisualization(Interceptor i, int columnSpan, String id, boolean noMarginTop) {
		td().style("padding:0px;");
		if (columnSpan > 1)
			colspan(""+columnSpan);
		if (i == null) {
			div().style("padding:2px 5px; margin: 10px; width: 299px;");
			raw("&nbsp;");
			end();
		} else {
			String shortDescription = i.getShortDescription();
			String longDescription = i.getLongDescription();
			boolean same = longDescription.equals(shortDescription);

			if (!TextUtil.isValidXMLSnippet(shortDescription)) {
				shortDescription = StringEscapeUtils.escapeHtml(shortDescription).replace("\n", "<br/>");
				if (same)
					longDescription = shortDescription;
			}
			if (!same && !TextUtil.isValidXMLSnippet(longDescription)) {
				longDescription = StringEscapeUtils.escapeHtml(longDescription).replace("\n", "<br/>");
			}

			shortDescription = shortDescription.replaceAll("\"/admin", "\"" + relativeRootPath + "/admin");
			longDescription = longDescription.replaceAll("\"/admin", "\"" + relativeRootPath + "/admin");

			String did = "d" + id;
			div().id(did).style("border: 1px solid black; padding:8px 5px; margin: 10px;overflow-x: auto; background-color: #FFC04F;" +
					(columnSpan == 1 ? "width: 299px;" : "width: 630px;") + (noMarginTop ? "margin-top: 0px;" : ""));

			String iid = "i" + id;
			div().id("i"+id);
			createHelpIcon(i, id);
			if (shortDescription.length() > 0 && !longDescription.equals(shortDescription)) {
				createExpandIcon(i, id);
			}
			end();
			createShowIconsScript(did, iid);

			div().classAttr("name");
			text(i.getDisplayName());
			end();
			if (shortDescription.length() > 0) {
				div().style("padding-top: 4px;");
				String sid = "s" + id;
				div().id(sid);
				raw(shortDescription);
				if (!longDescription.equals(shortDescription)) {
					String aid = "a" + id;
					String lid = "l" + id;
					String eid = "e" + id;
					String cid = "c" + id;
					a().id(aid).href("#").text("...").end();
					end();
					div().id(lid).style("margin: 0px;");
					raw(longDescription);
					end();
					script();
					raw("jQuery(document).ready(function() {\r\n" +
							"  jQuery(\"#"+eid+"\").css('cursor', 'pointer');\r\n" +
							"  jQuery(\"#"+cid+"\").css('cursor', 'pointer');\r\n" +
							"  jQuery(\"#"+lid+"\").hide();\r\n" +
							"  jQuery(\"#"+cid+"\").hide();\r\n" +
							"  jQuery(\"#"+eid+"\").click(function()\r\n" +
							"  {\r\n" +
							"    jQuery(\"#"+sid+"\").hide();\r\n" +
							"    jQuery(\"#"+lid+"\").slideToggle(500);\r\n" +
							"    jQuery(\"#"+eid+"\").hide();\r\n" +
							"    jQuery(\"#"+cid+"\").show();\r\n" +
							"  });\r\n" +
							"  jQuery(\"#"+aid+"\").click(function()\r\n" +
							"  {\r\n" +
							"    jQuery(\"#"+sid+"\").hide();\r\n" +
							"    jQuery(\"#"+lid+"\").slideToggle(500);\r\n" +
							"    jQuery(\"#"+eid+"\").hide();\r\n" +
							"    jQuery(\"#"+cid+"\").show();\r\n" +
							"  });\r\n" +
							"  jQuery(\"#"+cid+"\").click(function()\r\n" +
							"  {\r\n" +
							"    jQuery(\"#"+sid+"\").show();\r\n" +
							"    jQuery(\"#"+lid+"\").slideToggle(500);\r\n" +
							"    jQuery(\"#"+cid+"\").hide();\r\n" +
							"    jQuery(\"#"+eid+"\").show();\r\n" +
							"  });\r\n" +
							"});\r\n" +
							"</script>\r\n" +
							"\r\n" +
							"");
				}
				end();
				end();
			}
			end();
		}
		end();
	}

	private void createExpandIcon(Interceptor i, String id) {
		div().style("float:right;");
		span().id("e" + id).classAttr("ui-icon ui-icon-triangle-1-w").title("expand").end();
		end();
		div().style("float:right;");
		span().id("c" + id).classAttr("ui-icon ui-icon-triangle-1-s").title("collapse").end();
		end();
	}

	private void createHelpIcon(Interceptor i, String id) {
		String helpId = i.getHelpId();
		if (helpId != null) {
			div().style("float:right;");
			a().href("http://membrane-soa.org/service-proxy-doc/" + getVersion() + "/configuration/reference/" + helpId + ".htm");
			span().classAttr("ui-icon ui-icon-help").title("help").end();
			end();
			end();
		}
	}

	private void createShowIconsScript(String did, String iid) {
		script();
		raw("jQuery(document).ready(function() {\r\n" +
				"  jQuery(\"#"+iid+"\").hide();\r\n" +
				"  jQuery(\"#"+did+"\").hover(function()\r\n" +
				"  {\r\n" +
				"    jQuery(\"#"+iid+"\").show();\r\n" +
				"  }, function()\r\n" +
				"  {\r\n" +
				"    jQuery(\"#"+iid+"\").hide();\r\n" +
				"  });\r\n" +
				"});\r\n" +
				"");
		end();
	}

	/**
	 * @return The major and minor part of the version, e.g. "3.4"; or "current" if the version could not be parsed.
	 */
	private String getVersion() {
		String v = Constants.VERSION;
		int p = v.indexOf('.');
		if (p == -1)
			return "current";
		p = v.indexOf('.', p+1);
		if (p == -1)
			return "current";
		return v.substring(0, p);
	}

	private void createListenerRow(AbstractServiceProxy proxy) {
		tr();
		td().style("padding:0px;").colspan("2");
		div().style("border: 1px solid black; padding:8px 5px; margin: 0px 10px; overflow-x: auto; background: #73b9d7;" +
				"width: 630px;");
		div().classAttr("name");
		b();
		text("Listener");
		end();
		end();
		div().style("padding-top: 4px;");
		text("Virtual Host: " + proxy.getKey().getHost());
		br();
		if (proxy.getKey().getPort() != -1) {
			text("Port: " + proxy.getKey().getPort());
			br();
		}
		text("Path: " + proxy.getKey().getPath());
		br();
		text("Method: " + proxy.getKey().getMethod());
		if (proxy.getSslInboundContext() != null) {
			br();
			text("SSL: yes");
		}
		end();
		end();
		end();
		end();
	}

	public void createServiceProxyVisualization(AbstractServiceProxy proxy, String relativeRootPath) {
		List<Interceptor> leftStack = new ArrayList<Interceptor>(), rightStack = new ArrayList<Interceptor>();
		List<Interceptor> list = new ArrayList<Interceptor>(proxy.getInterceptors());
		list.add(new AbstractInterceptor() { // fake interceptor so that both stacks end with the same size
			@Override
			public EnumSet<Flow> getFlow() {
				return Flow.Set.REQUEST_RESPONSE;
			}});
		// build left and right stacks
		for (Interceptor i : list) {
			EnumSet<Flow> f = i.getFlow();
			if (i instanceof ResponseInterceptor) {
				for (Interceptor i2 : ((ResponseInterceptor)i).getInterceptors())
					rightStack.add(i2);
			} else if (i instanceof RequestInterceptor){
				for (Interceptor i3 : ((RequestInterceptor)i).getInterceptors())
					leftStack.add(i3);
			} else if (f.contains(Flow.REQUEST)) {
				if (f.contains(Flow.RESPONSE)) {
					// fill left and right to same height
					while (leftStack.size() < rightStack.size())
						leftStack.add(null);
					while (rightStack.size() < leftStack.size())
						rightStack.add(null);
					// put i into both
					leftStack.add(i);
					rightStack.add(i);
				} else {
					leftStack.add(i);
				}
			} else {
				if (f.contains(Flow.RESPONSE)) {
					rightStack.add(i);
				}
			}
		}

		boolean noTarget = proxy.getTargetURL() == null && proxy.getTargetHost() == null;

		table().cellspacing("0px").cellpadding("0px").classAttr("spv").style("width:662px");
		createListenerRow(proxy);
		if (leftStack.size() > 1 || !noTarget) {
			createBeginArrowsRow();
			for (int i = 0; i < leftStack.size() - 1 - (noTarget ? 1 : 0); i++) {
				tr().style("background:url(\""+relativeRootPath+"/admin/images/spv-middle.png\");background-repeat:repeat-y;display:inline-table");
				createInterceptorRow(leftStack, rightStack, i, false);
				end();
			}
			createEndArrowsRow();
			if (noTarget) {
				tr();
				createInterceptorRow(leftStack, rightStack, leftStack.size()-2, true);
				end();
			} else {
				createTargetRow(proxy);
			}
		}
		end();
	}

	private void createInterceptorRow(List<Interceptor> leftStack, List<Interceptor> rightStack, int i, boolean noMarginTop) {
		if (leftStack.get(i) == rightStack.get(i)) {
			createInterceptorVisualization(leftStack.get(i), 2, "spv_l" + i, noMarginTop);
		} else {
			createInterceptorVisualization(leftStack.get(i), 1, "spv_l" + i, noMarginTop);
			createInterceptorVisualization(rightStack.get(i), 1, "spv_r" + i, noMarginTop);
		}
	}

	private void createBeginArrowsRow() {
		tr();
		td().style("padding:0px;background:url(\""+relativeRootPath+"/admin/images/spv-top.png\");"+
				"background-repeat:repeat-y;height:14px;").colspan("2");
		end();
		end();
	}

	private void createEndArrowsRow() {
		tr().style("background:url(\""+relativeRootPath+"/admin/images/spv-bottom.png\");background-repeat:repeat-y;height:14px;");
		td().style("padding:0px;").colspan("2");
		end();
		end();
	}

	private void createTargetRow(AbstractServiceProxy proxy) {
		tr();
		td().style("padding:0px;").colspan("2");
		div().style("border: 1px solid black; padding:8px 5px; margin: 0px 10px; overflow-x: auto; background: #73b9d7;" +
				"width: 630px;");
		div().classAttr("name");
		b();
		text("Target");
		end();
		end();
		div().style("padding-top: 4px;");
		if (proxy.getTargetURL() == null) {
			text("Host: " + proxy.getTargetHost());
			br();
			text("Port: " + proxy.getTargetPort());
		} else {
			text("URL: " + proxy.getTargetURL());
		}
		if (proxy.getSslOutboundContext() != null) {
			br();
			text("SSL: yes");
		}
		end();
		end();
		end();
		end();
	}

	protected void creatExchangeMetaTable(String id)
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0",
				"class", "display", "id", id);
		thead();
		tr();
		createThs("Property", "Value");
		end();
		end();
		tbody();
		end();
		end();
	}

	protected void creatHeaderTable(String id)
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0",
				"class", "display", "id", id);
		thead();
		tr();
		createThs("Header Field", "Value");
		end();
		end();
		tbody();
		end();
		end();
	}

	protected void createClientsStatisticsTable()
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0",
				"class", "display", "id", "clients-table");
		thead();
		tr();
		createThs("Client", "Exchanges", "Min ms", "Max ms", "Avg ms");
		end();
		end();
		tbody();
		end();
		end();
	}

	protected void createMessageStatisticsTable()
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0",
				"class", "display", "id", "message-stat-table");
		thead();
		tr();
		createThs("Time", "Status Code", "Proxy", "Method", "Path", "Client",
				"Server", "Request Content-Type", "Request Content-Length",
				"Response Content-Type", "Response Content-Length", "Duration ms");
		end();
		end();
		tbody();
		end();
		end();
	}

	protected void createLogConfigurationEditor() {
		Logger root = Logger.getRootLogger();
		Level rootLevel = root.getLevel();

		if (readOnly) {
			p().text("The current root log level is " + rootLevel.toString() + ".").end();
			return;
		}

		form().id("changeRootLogLevelForm").action("/admin/log/level").method("POST");
		text("Change the root log level to: ");
		select().name("loglevel").onchange("this.form.submit()");
		for (Level l : new Level[] { OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL }) {
			option().value("" + l.toInt());
			if (rootLevel.equals(l))
				selected("true");
			text(l.toString());
			end();
		}
		end();
		end();

		form().id("replaceLogConfigurationForm").action("/admin/log/config").method("POST");
		text("");
		textarea().style("font-family:monospace;").name("logconfig").cols("120").rows("20").text("Paste new Log4j config here.").end();
		br();
		input().type("submit").value("Change log4j config");
		end();
	}
}

