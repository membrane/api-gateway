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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import org.apache.commons.logging.*;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.HttpUtil;

public class AdministratorInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(AdministratorInterceptor.class.getName());
	
	private Pattern pattern = Pattern.compile("/admin/?([^/]*)(/[^/\\?]*)?(\\?.*)?");

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
	
		log.debug("request: "+ exc.getOriginalRequestUri());
		
		if (matches(exc,"",null))  {
			return respond(exc, getMainPage(getParams(exc)));
		}
		
		if (matches(exc,"rule","details")) {
			return respond(exc,getMainPage(getParams(exc)));
		} 
		if (matches(exc,"rule","delete")) {
			deleteForwardingRule(getParams(exc));
			return respond(exc, getMainPage(getParams(exc)));
		} 

		if (matches(exc,"fwd-rule","save")) {
			addForwardingRule(getParams(exc));
			return respond(exc, getMainPage(getParams(exc)));
		} 

		if (matches(exc,"proxy-rule","save")) {
			addProxyRule(getParams(exc));
			return respond(exc, getMainPage(getParams(exc)));
		} 
		
		return Outcome.CONTINUE;		
	}

	private String getMainPage(final Map<String, String> params) throws Exception {
		StringWriter writer = new StringWriter();
		new AdminPageBuilder(writer,router,params) {{
				html();
				  createHead("Membrane Administrator");
				  body();
				  	div().id("tabs");
					  	ul();
					  		li();
					  			a().href("#tabs-1").text("Rules").end();
					  		end();
					  		li();
					  			a().href("#tabs-2").text("Transport").end();
					  		end();
					  		li();
				  				a().href("#tabs-3").text("System").end();
				  			end();
					  	end();
					  	createRulesTab();
						createTransportTab();				  	
					  	createSystemTab();		
						createFwdRuleDetailsDialogIfNeeded();		
						createProxyRuleDetailsDialogIfNeeded();
				  	end();
				  	createSkript();
				endAll(); 
				done();
			}				
		};
	    return writer.getBuffer().toString();
	}	
	
	private void deleteForwardingRule(Map<String, String> params) throws Exception {
		router.getRuleManager().removeRule(RuleUtil.findRuleByIdentifier(router,params.get("name")));		
	}

	private void addForwardingRule(Map<String, String> params) throws Exception {
		logAddFwdRuleParams(params);

		Rule r = new ForwardingRule(new ForwardingRuleKey("*", params.get("method"), ".*", getPortParam(params)), 
				 params.get("targetHost"), getTargetPortParam(params));
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
	}

	private void addProxyRule(Map<String, String> params) throws Exception {
		log.debug("adding proxy rule");
		log.debug("name: "+params.get("name"));
		log.debug("port: "+params.get("port"));

		Rule r = new ProxyRule(new ProxyRuleKey(Integer.parseInt(params.get("port")))); 
		r.setName(params.get("name"));
		router.getRuleManager().addRuleIfNew(r);
	}



	private boolean matches(Exchange exc, String ctrl, String action) {
		Matcher m = pattern.matcher(exc.getOriginalRequestUri());
		if (!m.matches() || !ctrl.equals(m.group(1))) return false;
  	    return ( action == null || ("/"+action).equals(m.group(2)));			   		
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
		Map<String, String> params = new HashMap<String, String>();
		
		URI jUri = new URI(exc.getOriginalRequestUri());
		String q = jUri.getQuery();
		if (q == null) {
			if (hasNoFormParams(exc)) 
				return params;
			q = new String(exc.getRequest().getBody().getRaw());// TODO getBody().toString() doesn't work.				
		}

		for (String p : q.split("&")) {
			params.put(p.split("=")[0], URLDecoder.decode(p.split("=")[1],"UTF-8"));
		}
		return params;		
	}

	private boolean hasNoFormParams(Exchange exc) throws IOException {
		return !"application/x-www-form-urlencoded".equals(exc.getRequest().getHeader().getFirstValue("Content-Type"))||
			 exc.getRequest().isBodyEmpty();
	}

	private Outcome respond(Exchange exc, String body) {
		Response res = new Response();
		res.setStatusCode(200);
		res.setStatusMessage("OK");
		res.setHeader(createHeader());

		res.setBody(new Body(body));
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
		log.debug("name: "+params.get("name"));
		log.debug("port: "+params.get("port"));
		log.debug("client host: "+params.get("clientHost"));
		log.debug("method: "+params.get("method"));
		log.debug("path: "+params.get("path"));
		log.debug("target host: "+params.get("targetHost"));
		log.debug("target port: "+params.get("targetPort"));
	}


	
//	private String getAddForwardingRulePage() {
//		StringWriter writer = new StringWriter(); 
//		new HtmlBuilder(writer,router) {{
//			html();
//			  createHead("Membrane Administrator - Add New ForwardingRule");
//			  body();
//			  	h1().text("Add New ForwardingRule");
//			  	form().action("/fwd-rule/save").method("POST").id("formular");
//			  		table();
//			  			createParam("Name", "name", "validate[required]");
//			  			createParam("Port", "port", "validate[required,custom[integer]]");
//			  			createParam("Target Host", "targetHost", "validate[required]");
//			  			createParam("Target Port", "targetPort", "validate[required,custom[integer]]");
//			  		end();			  		
//			  		input().value("Add").type("submit").classAttr("mb-button").end();
//			  	end();
//			  	createSkript();
//			endAll();
//			done();
//		}
//
//		};
//		
//		return writer.toString();
//	}
//
//	private String getAddProxyRulePage() {
//		StringWriter writer = new StringWriter(); 
//		new HtmlBuilder(writer,router) {{
//			html();
//			  createHead("Membrane Administrator - Add New ProxyRule");
//			  body();
//			  	h1().text("Add New ProxyRule");
//			  	form().action("/proxy-rule/save").method("POST");
//			  		table();
//			  			createParam("Name", "name", "validate[required]");
//			  			createParam("Port", "port", "validate[required,custom[integer]]");
//			  		end();
//			  		input().value("Add").type("submit").classAttr("mb-button").end();
//			  	end();
//			  	createSkript();
//			endAll();
//			done();
//		}};
//		
//		return writer.toString();
//	}	
//	private String getForwardingRuleDetailsPage(final Map<String, String> map) throws Exception {
//		StringWriter writer = new StringWriter(); 
//		new HtmlBuilder(writer,router) {{
//			html();
//			  createHead("Membrane Administrator - Forwarding Rule Details");
//			  body();
//			  	h1().text("Forwarding Rule Details").end();
//				table();
//					ForwardingRule rule = (ForwardingRule) findRuleByIdentifier(map.get("name"));
//					createTr("Name",rule.toString());
//					createTr("Listen Port",""+rule.getKey().getPort());
//					createTr("Client Host",rule.getKey().getHost());
//					createTr("Method",rule.getKey().getMethod());
//					createTr("Path",rule.getKey().getPath());
//					createTr("Target Host",rule.getTargetHost());
//					createTr("Target Port",""+rule.getTargetPort());
//				end();
//				h2().text("Interceptors").end();
//				createInterceptorTable(rule.getInterceptors());
//			  end();
//    		  createSkript();			  	
//		    endAll();
//			done();
//		}};
//
//	    return writer.getBuffer().toString();
//	}
	
}
