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

import static com.predic8.membrane.core.util.URLUtil.parseQueryString;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.lang.WordUtils;
import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.HttpUtil;

public class AdministrationInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(AdministrationInterceptor.class
			.getName());

	private Pattern pattern = Pattern
			.compile("/admin/?([^/]*)(/[^/\\?]*)?(\\?.*)?");

	public AdministrationInterceptor() {
		name = "Administrator";
		priority = 1000;
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {

		log.debug("request: " + exc.getOriginalRequestUri());

		return dipatchRequest(exc);
		
	}

	public String handleRequest(Map<String, String> params) throws Exception {
		return getRulesPage(params);
	}

	public String handleFruleShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
			@Override
			protected int getSelectedTab() {
				return 0;
			}
		
			@Override
			protected void createTabContent() throws Exception {
				h1().text("Forwarding Rule Details").end();
				table();
					ForwardingRule rule = (ForwardingRule) RuleUtil
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
		}.createPage();
	}

	public String handlePruleShowRequest(final Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
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
		
		}.createPage();
	}

	public String handleFruleSaveRequest(Map<String, String> params) throws Exception {
		logAddFwdRuleParams(params);
		
		Rule r = new ForwardingRule(new ForwardingRuleKey("*",
				params.get("method"), ".*", getPortParam(params)),
				params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
		
		return getRulesPage(params);
	}
	
	public String handlePruleSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding proxy rule");
		log.debug("name: " + params.get("name"));
		log.debug("port: " + params.get("port"));
		
		Rule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port"))));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
		return getRulesPage(params);
	}

	public String handleRuleDeleteRequest(Map<String, String> params)
	  throws Exception {
			router.getRuleManager().removeRule(
					RuleUtil.findRuleByIdentifier(router, params.get("name")));
			return getRulesPage(params);
	}
	
	public String handleTransportRequest(Map<String, String> params)
	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
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
		
		}.createPage();
	}

	public String handleSystemRequest(final Map<String, String> params)
   	  throws Exception {
		StringWriter writer = new StringWriter();
		return new AdminPageBuilder(writer, router, params) {
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
		}.createPage();
	}

	public String handleClustersRequest(Map<String, String> params) throws Exception {
		return getClustersPage(params);
	}

	public String handleClustersShowRequest(Map<String, String> params) throws Exception {
		return getClusterPage(params);
	}

	public String handleClustersSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding cluster");
		log.debug("name: " + params.get("name"));
		
		router.getClusterManager().addCluster(params.get("name"));
		
		return getClustersPage(params);
	}

	public String handleNodeSaveRequest(Map<String, String> params) throws Exception {
		log.debug("adding node");
		log.debug("cluster: " + params.get("cluster"));
		log.debug("host: " + params.get("host"));
		log.debug("port: " + params.get("port"));
		
		router.getClusterManager().up(params.get("cluster"),
				                      params.get("host"), 
				                      Integer.parseInt(params.get("port")));
		return getClusterPage(params);
	}

	public String handleNodeUpRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().up(params.get("cluster"), params.get("host"),
				                      Integer.parseInt(params.get("port")));
		return getClusterPage(params);
	}

	public String handleNodeDownRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().down(params.get("cluster"), params.get("host"),
				                        Integer.parseInt(params.get("port")));
		return getClusterPage(params);
	}

	public String handleNodeDeleteRequest(Map<String, String> params) throws Exception {
		router.getClusterManager().delete(params.get("cluster"), params.get("host"),
				                          Integer.parseInt(params.get("port")));
		return getClusterPage(params);
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
		Matcher m = pattern.matcher(exc.getOriginalRequestUri());
		if (!m.matches()) return Outcome.CONTINUE;
		
		String action = m.group(2);
		String mName = "handle"+WordUtils.capitalize(m.group(1))+
						(action == null?"":WordUtils.capitalize(action.substring(1)))+"Request";
		
		return respond(exc, (String)getClass().getMethod(mName, Map.class).invoke(this, new Object[] {getParams(exc)}));
	}

	private Header createHeader() {
		Header header = new Header();
		header.setContentType("text/html;charset=utf-8");
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		return header;
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
				.getHeader().getFirstValue("Content-Type"))
				|| exc.getRequest().isBodyEmpty();
	}

	private Outcome respond(Exchange exc, String page) throws Exception {
		Response res = new Response();
		res.setStatusCode(200);
		res.setStatusMessage("OK");
		res.setHeader(createHeader());

		res.setBody(new Body(page));
		exc.setResponse(res);
		return Outcome.ABORT;
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
}
