package com.predic8.membrane.core.interceptor.administration;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.*;

public class HtmlBuilder extends Html {

	Router router;
	
	public HtmlBuilder(Writer writer, Router router) {
		super(writer);
		this.router = router;
	}

	protected void createHead(String title) {
		head();
		    title().text(title).end();
			style().attr("type", "text/css").text("@import '/static/datatables/css/demo_table_jui.css';\n" +
												  "@import '/static/jquery-ui/css/custom-theme/jquery-ui-1.8.13.custom.css';"+
					                              "@import '/static/css/membrane.css';").end();	
			script().src("/static/jquery/jquery-1.6.1.js").end();
			script().src("/static/datatables/js/jquery.dataTables.min.js").end();
			script().src("/static/jquery-ui/js/jquery-ui-1.8.13.custom.min.js").end();
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

	protected void createRulesTab() throws Exception {
		div().id("tabs-1");
			h2().text("Rules").end();

			h3().text("Forwarding Rules").end();
			createFwdRulesTable();
			a().href("fwd-rule/create").classAttr("mb-button").text("Add rule").end();
			h3().text("Proxy Rules").end();
			createProxyRulesTable();
		end();
	}

	protected void createFwdRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name", "Listen Port", "Client Host", "Method","Path","Target Host","Target Port");
			    end();
			end();
			tbody();
				for (ForwardingRule rule : getForwardingRules()) {
					tr();
						td().a().href("fwd-rule/details?name="+URLEncoder.encode(rule.toString(),"UTF-8")).text(rule.toString()).end().end();
						createTds(""+rule.getKey().getPort(),
						             rule.getKey().getHost(),
						             rule.getKey().getMethod(),
						             rule.getKey().getPath(),
						             rule.getTargetHost(),
						             ""+rule.getTargetPort());
					end();
				}
			end();
		end();
	}

	protected void createProxyRulesTable() throws UnsupportedEncodingException {
		table().attr("cellpadding", "0", "cellspacing", "0", "border", "0", "class", "display");
			thead();
				tr();
					createThs("Name", "Listen Port");
			    end();
			end();
			tbody();
				for (ProxyRule rule : getProxyRules()) {
					tr();
						createTds(rule.toString(),
								  ""+rule.getKey().getPort());
					end();
				}
			end();
		end();
	}

	protected List<ProxyRule> getProxyRules() {
		List<ProxyRule> rules = new LinkedList<ProxyRule>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ProxyRule)) continue;
			rules.add((ProxyRule) r);
		}			
		return rules;
	}

	protected List<ForwardingRule> getForwardingRules() {
		List<ForwardingRule> rules = new LinkedList<ForwardingRule>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (!(r instanceof ForwardingRule)) continue;
			rules.add((ForwardingRule) r);
		}			
		return rules;
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
