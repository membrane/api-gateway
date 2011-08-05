package com.predic8.membrane.core.interceptor.administration;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.rules.*;
import static com.predic8.membrane.core.util.URLUtil.*;
import static org.apache.commons.lang.time.DurationFormatUtils.*;

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
					createThs("Name", "Priority");
			    end();
			end();
			tbody();
				for (Interceptor i : interceptors) {
					tr();
						createTds(i.getDisplayName(), ""+i.getPriority());								  
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
				for (ServiceProxy rule : getForwardingRules()) {
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
			li().classAttr(getSelectedTabStyle(4, selected));
				createLink("Loadbalancer", "clusters", null, null);
			end();
		end();
	}
	
	protected void createAddClusterForm() {
		form().id("addClusterForm").action("/admin/clusters/save").method("POST");
			div()
				.span().text("Name").end()
				.span().input().type("text").id("name").name("name").classAttr("validate[required]").end(2) 
				.span().input().value("Add Cluster").type("submit").classAttr("mb-button").end(2);
			end();		  		
		end();
	}

	protected void createClustersTable()
			throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name", "#Nodes", "Health");
			    end();
			end();
			tbody();
				for (Cluster c : router.getClusterManager().getClusters()) {
					tr();
						td();
						createLink(c.getName(), "clusters", "show", createQueryString("cluster", c.getName()));
						end();
						
						createTds(String.valueOf(router.getClusterManager().getAllNodesByCluster(c.getName()).size()), 
								  getFormatedHealth(c.getName()));
					end();
				}
			end();
		end();
	}

	protected void createAddNodeForm() {
		form().id("addNodeForm").action("/admin/node/save").method("POST");
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

	protected void createStatusCodesTable(Map<Integer, Integer> statusCodes) throws Exception {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Status Code", "Count");
			    end();
			end();
			tbody();
				synchronized (statusCodes) {
					for (Map.Entry<Integer, Integer> codes : statusCodes.entrySet() ) {
						tr();
							createTds(""+codes.getKey(), ""+codes.getValue());
						end();				
					}					
				}
			end();
		end();
	}

	protected void createNodesTable() throws Exception {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Node", "Status", "Count", "Errors", "Time since last up", "Sessions", "Current Threads", "Action");
			    end();
			end();
			tbody();
				for (Node n : router.getClusterManager().getAllNodesByCluster(params.get("cluster"))) {
					tr();
						td();
						createLink(""+n.getHost()+":"+n.getPort(), "node", "show", 
								   createQueryString("cluster", params.get("cluster"), "host", n.getHost(),"port", ""+n.getPort() ));
						end();
						createTds( getStatusString(n), ""+n.getCounter(), 
								   String.format("%1$.2f%%", n.getErrors()*100),
								   formatDurationHMS(System.currentTimeMillis()-n.getLastUpTime()),
								   ""+router.getClusterManager().getSessionsByNode(params.get("cluster"),n).size(),
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
		return createQueryString("cluster", params.get("cluster"),"host", n.getHost(), "port", ""+n.getPort());
	}

	private void createIcon(String icon, String ctrl, String action, String tooltip, String query) {
		a().href(createHRef(ctrl, action, query)).span().classAttr("ui-icon "+icon).style("float:left;").title(tooltip).end(2);
	}

	private String getFormatedHealth(String name) {
		return String.format("%d up/ %d down", router.getClusterManager().getAvailableNodesByCluster(name).size(),
											   router.getClusterManager().getAllNodesByCluster(name).size() - router.getClusterManager().getAvailableNodesByCluster(name).size());
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

	private List<ServiceProxy> getForwardingRules() {
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
	
}
