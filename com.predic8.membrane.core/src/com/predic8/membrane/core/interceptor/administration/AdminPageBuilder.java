package com.predic8.membrane.core.interceptor.administration;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.*;

public class AdminPageBuilder extends Html {
	
	Router router;
	Map<String, String> params;
	
	public AdminPageBuilder(Writer writer, Router router, Map<String, String> params) {
		super(writer);
		this.router = router;
		this.params = params;
	}

	
	protected void createHead(String title) {
		head();
		    title().text(title).end();
			style().attr("type", "text/css").text("@import '/datatables/css/demo_table_jui.css';\n" +
												  "@import '/jquery-ui/css/custom-theme/jquery-ui-1.8.13.custom.css';"+
					                              "@import '/css/membrane.css';").end();	
			link().rel("stylesheet").href("/formValidator/validationEngine.jquery.css").type("text/css");
			script().src("/jquery/jquery-1.6.1.js").end();
			script().src("/datatables/js/jquery.dataTables.min.js").end();
			script().src("/jquery-ui/js/jquery-ui-1.8.13.custom.min.js").end();
			script().src("/formValidator/jquery.validationEngine-en.js").end();
			script().src("/formValidator/jquery.validationEngine.js").end();
		  end();
	}

	protected void createSkript() {
		script().text(							  	
			"$(function() {" +
			"		$('table.display').dataTable({" +
			"		  'bJQueryUI': true,"+
			"		  'sPaginationType': 'full_numbers'"+
			"		});"+
			"	    $('#tabs').tabs();"+
			"       $('.mb-button').button();"+
			"       $('form').validationEngine('attach', {promptPosition : 'bottomRight', scroll: false});"+
			"       $('form').submit(function() {"+
	        "                          return this.validationEngine('validate');"+
	    	"                       });"+			
			"});");
		end();
	}
	
	protected void createTransportTab() throws UnsupportedEncodingException {
		div().id("tabs-2");
			h2().text("Transport").end();

			h3().text("Backbone Interceptors").end();
			createInterceptorTable(router.getTransport().getBackboneInterceptors());

			h3().text("Transport Interceptors").end();
			createInterceptorTable(router.getTransport().getInterceptors());
		end();
	}

	private void createInterceptorTable(List<Interceptor> interceptors) {
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

	private void createAddFwdRuleForm() {
		form().id("addFwdRuleForm").action("/admin/fwd-rule/save").method("POST");
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

	private void createAddProxyRuleForm() {
		form().id("addProxyRuleForm").action("/admin/proxy-rule/save").method("POST");
			div()
				.span().text("Name").end()
				.span().input().type("text").id("p-name").name("name").classAttr("validate[required]").end(2) //id != name so that validation error reports on the page show of at the right places.
				.span().text("Listen Port").end()
				.span().input().type("text").id("p-port").name("port").size("5").classAttr("validate[required,custom[integer]]").end(2)
				.span().input().value("Add").type("submit").classAttr("mb-button").end(2);
			end();		  		
		end();
	}
	
	private void createFwdRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name", "Listen Port", "Client Host", "Method","Path","Target Host","Target Port","Actions");
			    end();
			end();
			tbody();
				for (ForwardingRule rule : getForwardingRules()) {
					tr();
						td().a().href("/admin/rule/details?fwd-rule-name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).text(rule.toString()).end().end();
						createTds(""+rule.getKey().getPort(),
						             rule.getKey().getHost(),
						             rule.getKey().getMethod(),
						             rule.getKey().getPath(),
						             rule.getTargetHost(),
						             ""+rule.getTargetPort());
						td().a().href("/admin/rule/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).span().classAttr("ui-icon ui-icon-trash").end(3);
					end();
				}
			end();
		end();
	}

	private void createProxyRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name", "Listen Port", "Actions");
			    end();
			end();
			tbody();
				for (ProxyRule rule : getProxyRules()) {
					tr();
						td().a().href("/admin/rule/details?proxy-rule-name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).text(rule.toString()).end().end();
						createTds(""+rule.getKey().getPort());
						td().a().href("/admin/rule/delete?name="+URLEncoder.encode(RuleUtil.getRuleIdentifier(rule),"UTF-8")).span().classAttr("ui-icon ui-icon-trash").end(3);
					end();
				}
			end();
		end();
	}

	protected void createRulesTab() throws Exception {
		div().id("tabs-1");
			h2().text("Rules").end();

			h3().text("Forwarding Rules").end();
			createFwdRulesTable();
		  	createAddFwdRuleForm();			
			h3().text("Proxy Rules").end();
			createProxyRulesTable();
		  	createAddProxyRuleForm();			
		end();
	}

	protected void createFwdRuleDetailsDialogIfNeeded() throws Exception {
		if (!params.containsKey("fwd-rule-name")) return;
		
		div().id("dialog");
		  	h1().text("Forwarding Rule Details").end();
			table();
				ForwardingRule rule = (ForwardingRule) RuleUtil.findRuleByIdentifier(router,params.get("fwd-rule-name"));
				createTr("Name",rule.toString());
				createTr("Listen Port",""+rule.getKey().getPort());
				createTr("Client Host",rule.getKey().getHost());
				createTr("Method",rule.getKey().getMethod());
				createTr("Path",rule.getKey().getPath());
				createTr("Target Host",rule.getTargetHost());
				createTr("Target Port",""+rule.getTargetPort());
			end();
			h2().text("Interceptors").end();
			createInterceptorTable(rule.getInterceptors());
		end();
		script().text(							  	
				"$(function() {" +
				"       $('#dialog').dialog({" +
				"						title:'Forwarding Rule Details',"+
				"						width:800," +
				"						height:550," +
				"						modal:true" +
				"					 });"+
				"});");
		end();			  				
	}

	protected void createProxyRuleDetailsDialogIfNeeded() throws Exception {
		if (!params.containsKey("proxy-rule-name")) return;
		
		div().id("dialog");
		  	h1().text("Proxy Rule Details").end();
			table();
				ProxyRule rule = (ProxyRule) RuleUtil.findRuleByIdentifier(router,params.get("proxy-rule-name"));
				createTr("Name",rule.toString());
				createTr("Listen Port",""+rule.getKey().getPort());
			end();
			h2().text("Interceptors").end();
			createInterceptorTable(rule.getInterceptors());
		end();
		script().text(							  	
				"$(function() {" +
				"       $('#dialog').dialog({" +
				"						title:'Proxy Rule Details',"+
				"						width:800," +
				"						height:400," +
				"						modal:true" +
				"					 });"+
				"});");
		end();			  							
	}
	
	private List<ProxyRule> getProxyRules() {
		List<ProxyRule> rules = new LinkedList<ProxyRule>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ProxyRule)) continue;
			rules.add((ProxyRule) r);
		}			
		return rules;
	}

	private List<ForwardingRule> getForwardingRules() {
		List<ForwardingRule> rules = new LinkedList<ForwardingRule>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ForwardingRule)) continue;
			rules.add((ForwardingRule) r);
		}			
		return rules;
	}
	
	private void createThs(String... data) {			
		for (String d : data) {
			th().text(d).end();
		}
	}
	
	private void createTds(String... data) {			
		for (String d : data) {
			td().text(d).end();
		}
	}

	private void createTr(String... data) {
		tr();
		for (String d : data)
			td().text(d).end();
		end();
	}	
	
	protected void createSystemTab() {
		div().id("tabs-3");
			h2().text("System").end();

			long total = Runtime.getRuntime().totalMemory();
			long free = Runtime.getRuntime().freeMemory();
			p().text("Availabe system memory: " + total).end();
			p().text("Free system memory: " + free).end();
		end();
	}	
}
