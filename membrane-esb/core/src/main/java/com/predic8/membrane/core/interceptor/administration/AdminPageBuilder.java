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

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.Cluster;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.Node;
import com.predic8.membrane.core.interceptor.balancer.Session;
import com.predic8.membrane.core.rules.AbstractProxy;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.StatisticCollector;

public class AdminPageBuilder extends Html {
	
	Router router;
	Map<String, String> params;
	private StringWriter writer;

	static public String createHRef(String ctrl, String action, String query) {
		return "/admin/"+ctrl+(action!=null?"/"+action:"")+(query!=null?"?"+query:"");
	}
	
	public AdminPageBuilder(StringWriter writer, Router router, Map<String, String> params) {
		super(writer);
		this.router = router;
		this.params = params;
		this.writer = writer;
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
		  	end();
		endAll(); 
		done();	
		return writer.getBuffer().toString();
	}
	
	protected String getTitle() {
		return "Membrane Administration";
	}
	
	protected int getSelectedTab() {
		return 0;
	}
	
	protected void createTabContent() throws Exception {
	}


	protected void createHead() {
		head();
		    title().text(getTitle()).end();
			style().attr("type", "text/css").text("@import '/datatables/css/demo_table_jui.css';\n" +
												  "@import '/jquery-ui/css/custom-theme/jquery-ui-1.8.13.custom.css';"+
					                              "@import '/css/membrane.css';").end();	
			link().rel("stylesheet").href("/formValidator/validationEngine.jquery.css").type("text/css");
			script().src("/jquery/jquery-1.6.1.js").end();
			script().src("/datatables/js/jquery.dataTables.min.js").end();
			script().src("/jquery-ui/js/jquery-ui-1.8.13.custom.min.js").end();
			script().src("/formValidator/jquery.validationEngine-en.js").end();
			script().src("/formValidator/jquery.validationEngine.js").end();
			script().src("/js/membrane.js").end();
			createMetaElements();
		  end();
	}

	protected void createMetaElements() {}

	protected void createMeta(String... meta) {
		for (int i = 0; i< meta.length; i+=2) {
			meta().httpEquiv(meta[i]).content(meta[i+1]).end();
		}
	}

	protected void createInterceptorTable(List<Interceptor> interceptors) {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name");
			    end();
			end();
			tbody();
				for (Interceptor i : interceptors) {
					tr();
						createTds(i.getDisplayName());								  
					end();
				}
			end();
		end();
	}

	protected void createAddFwdRuleForm() {
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
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name", "Listen Port", "Client Host", "Method","Path","Target Host","Target Port","Count","Actions");
			    end();
			end();
			tbody();
				for (ServiceProxy rule : getServiceProxies()) {
					tr();
						td();
							createLink(rule.toString(), "service-proxy", "show", createQueryString("name",RuleUtil.getRuleIdentifier(rule)));
						end();
						createTds(""+rule.getKey().getPort(),
						             rule.getKey().getHost(),
						             rule.getKey().getMethod(),
						             rule.getKey().getPath(),
						             rule.getTargetHost(),
						             ""+rule.getTargetPort(),
						             ""+rule.getCount());
						td().a().href("/admin/service-proxy/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).span().classAttr("ui-icon ui-icon-trash").end(3);
					end();
				}
			end();
		end();
	}

	protected void createProxyRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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
						createTds(""+rule.getKey().getPort(),
								  ""+rule.getCount());
						td().a().href("/admin/proxy/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).span().classAttr("ui-icon ui-icon-trash").end(3);
					end();
				}
			end();
		end();
	}

	protected void createTabs(int selected) throws Exception {
		ul().classAttr("ui-tabs-nav ui-helper-reset ui-helper-clearfix ui-widget-header ui-corner-all");
			li().classAttr(getSelectedTabStyle(0, selected));
				a().href("/admin").text("ServiceProxies").end();
			end();
			li().classAttr(getSelectedTabStyle(1, selected));
				createLink("Proxies", "proxy", null, null);
			end();
			li().classAttr(getSelectedTabStyle(2, selected));
				createLink("Transport", "transport", null, null);
			end();
			li().classAttr(getSelectedTabStyle(3, selected));
				createLink("System", "system", null, null);
			end();
			if (BalancerUtil.hasLoadBalancing(router)) {
				li().classAttr(getSelectedTabStyle(4, selected));
					createLink("Load Balancing", "balancers", null, null);
				end();
			}
			li().classAttr(getSelectedTabStyle(5, selected));
				createLink("Statistics", "statistics", null, null);
			end();
		end();
	}
	
	protected void createAddClusterForm(String balancerName) {
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
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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


	protected void createNodesTable(String balancerName) throws Exception {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
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
							createIcon("ui-icon-trash", "node", "delete", "delete", createQuery4Node(n));
						end();
					end();
				}
			end();
		end();
	}
	
	private String getStatusString(Node n) {
		switch (n.getStatus()) { 
			case TAKEOUT:
				return "In take out";
		}
		return ""+n.getStatus();
	}

	private String createQuery4Node(Node n) throws UnsupportedEncodingException {
		return createQueryString("balancer", AdminConsoleInterceptor.getBalancerParam(params), 
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

	private List<ServiceProxy> getServiceProxies() {
		List<ServiceProxy> rules = new LinkedList<ServiceProxy>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ServiceProxy)) continue;
			rules.add((ServiceProxy) r);
		}			
		return rules;
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
	
	private void createInterceptorVisualization(Interceptor i, int columnSpan, String id) {
		td().style("padding:0px;");
		if (columnSpan > 1)
			colspan(""+columnSpan);
		if (i == null) {
			div().style("padding:2px 5px; margin: 10px;");
			raw("&nbsp;");
			end();
		} else {
			div().style("border: 1px solid black; padding:8px 5px; margin: 10px;overflow-x: auto; background-color: #FFC04F;" + 
					(columnSpan == 1 ? "width: 298px;" : "width: 630px;"));
			div();
			text(i.getDisplayName());
			end();
			String shortDescription = i.getShortDescription();
			String longDescription = i.getLongDescription();
			if (shortDescription.length() > 0) {
				div().style("padding-top: 4px;");
				String sid = "s" + id;
				small().id(sid);
				text(i.getShortDescription());
				if (!longDescription.equals(shortDescription)) {
					a().href("#").text("Details").end();
					String tid = "l" + id;
					end();
					small().id(tid).style("margin: 0px; cursor: pointer;");
					text(longDescription);
					end();
					script();
					raw("jQuery(document).ready(function() {\r\n" +
						"  jQuery(\"#"+tid+"\").hide();\r\n" +
						"  jQuery(\"#"+sid+"\").css('cursor', 'pointer');\r\n" +
						"  jQuery(\"#"+sid+"\").click(function()\r\n" +
						"  {\r\n" +
						"    jQuery(\"#"+sid+"\").hide();\r\n" +
						"    jQuery(\"#"+tid+"\").slideToggle(500);\r\n" +
						"  });\r\n" +
						"  jQuery(\"#"+tid+"\").click(function()\r\n" +
						"  {\r\n" +
						"    jQuery(\"#"+sid+"\").show();\r\n" +
						"    jQuery(\"#"+tid+"\").slideToggle(500);\r\n" +
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
	
	protected void createServiceProxyVisualization(ServiceProxy proxy) {
		List<Interceptor> leftStack = new ArrayList<Interceptor>(), rightStack = new ArrayList<Interceptor>();
		List<Interceptor> list = new ArrayList<Interceptor>(proxy.getInterceptors());
		list.add(new AbstractInterceptor() { // fake interceptor so that both stacks end with the same size
			public Flow getFlow() {
				return Flow.REQUEST_RESPONSE;
		}});
		// build left and right stacks
		for (Interceptor i : list) {
			switch (i.getFlow()) {
			case REQUEST:	
				leftStack.add(i);
				break;
			case RESPONSE:
				rightStack.add(i);
				break;
			case REQUEST_RESPONSE:
				// fill left and right to same height
				while (leftStack.size() < rightStack.size())
					leftStack.add(null);
				while (rightStack.size() < leftStack.size())
					rightStack.add(null);
				// put i into both
				leftStack.add(i);
				rightStack.add(i);
			}
		}

		table().cellspacing("0px").cellpadding("0px");
			tr();
				td().style("padding:0px;").colspan("2");
					div().style("border: 1px solid black; padding:8px 5px; margin: 0px 10px; overflow-x: auto; background: #73b9d7;" + 
							"width: 630px;");
						div();
							b();
								text("Listener");
							end();
						end();
						div().style("padding-top: 4px;");
							small();
								text("Host: " + proxy.getKey().getHost());
								br();
								text("Port: " + proxy.getKey().getPort());
								br();
								text("Path: " + proxy.getKey().getPath());
								br();
								text("Method: " + proxy.getKey().getMethod());
							end();
						end();
					end();
				end();
			end();
			tr();
				td().style("padding:0px;background:url(\"/images/spv-top.png\");background-repeat:repeat-y;height:14px;").colspan("2");
				end();
			end();
			for (int i = 0; i < leftStack.size() - 1; i++) {
				tr().style("background:url(\"/images/spv-middle.png\");background-repeat:repeat-y;");
					if (leftStack.get(i) == rightStack.get(i)) {
						createInterceptorVisualization(leftStack.get(i), 2, "spv_l" + i);
					} else {
						createInterceptorVisualization(leftStack.get(i), 1, "spv_l" + i);
						createInterceptorVisualization(rightStack.get(i), 1, "spv_r" + i);
					}
				end();
			}
			tr().style("background:url(\"/images/spv-bottom.png\");background-repeat:repeat-y;height:14px;");
				td().style("padding:0px;").colspan("2");
				end();
			end();
			tr();
				td().style("padding:0px;").colspan("2");
					div().style("border: 1px solid black; padding:8px 5px; margin: 0px 10px; overflow-x: auto; background: #73b9d7;" + 
							"width: 630px;");
						div();
							b();
								text("Target");
							end();
						end();
						div().style("padding-top: 4px;");
							small();
								if (proxy.getTargetURL() == null) {
									text("Host: " + proxy.getTargetHost());
									br();
									text("Port: " + proxy.getTargetPort());
								} else {
									text("URL: " + proxy.getTargetURL());
								}
							end();
						end();
					end();
				end();
			end();
		end();
	}
	
}
