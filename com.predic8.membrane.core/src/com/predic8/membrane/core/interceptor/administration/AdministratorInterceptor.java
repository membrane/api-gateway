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

import java.io.StringWriter;
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
	
	private Pattern pattern = Pattern.compile("/([^/]*)(/[^/\\?]*)?(\\?.*)?");

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
	
		log.debug("request: "+ exc.getOriginalRequestUri());
		
		if (matches(exc,"",null))  {
			exc.setResponse(createHTMLResponse(getMainPage()));
			return Outcome.ABORT;
		}

		if (matches(exc,"fwd-rule","create")) {
			exc.setResponse(createHTMLResponse(getAddForwardingRule()));
			return Outcome.ABORT;
		} 
		if (matches(exc,"fwd-rule","details")) {
			exc.setResponse(createHTMLResponse(getForwardingRuleDetails(getParams(exc))));
			return Outcome.ABORT;
		} 
		
		log.debug("no controller or action found: ");
		exc.setResponse(HttpUtil.createNotFoundResponse());
		return Outcome.ABORT;		
	}

	private boolean matches(Exchange exc, String ctrl, String action) {
		Matcher m = pattern.matcher(exc.getOriginalRequestUri());
		if (!m.matches() || !ctrl.equals(m.group(1))) return false;
  	    return ( action == null || ("/"+action).equals(m.group(2)));			   		
	}
	
	private Map<String, String> getParams(Exchange exc) throws Exception {
		Map<String, String> params = new HashMap<String, String>();
		
		URI jUri = new URI(exc.getOriginalRequestUri());
		if (jUri.getQuery() == null) return params;

		for (String p : jUri.getQuery().split("&")) {
			params.put(p.split("=")[0], URLDecoder.decode(p.split("=")[1],"UTF-8"));
		}
		return params;		
	}
	
	private Response createHTMLResponse(String body) {
		Response response = new Response();
		response.setStatusCode(200);
		response.setStatusMessage("OK");
		response.setHeader(createHeader());

		response.setBody(new Body(body));
		return response;
	}

	private String getForwardingRuleDetails(final Map<String, String> map) {
		StringWriter writer = new StringWriter(); 
		new HtmlBuilder(writer,router) {{
			html();
			  createHead("Membrane Administrator - Forwarding Rule Details");
			  body();
			  	h1().text("Forwarding Rule Details").end();
				table();
					ForwardingRule rule = (ForwardingRule) findRuleByName(map.get("name"));
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
    		  createSkript();			  	
		    endAll();
			done();
		}};

	    return writer.getBuffer().toString();
	}

	private String getMainPage() throws Exception {
		StringWriter writer = new StringWriter();
		new HtmlBuilder(writer,router) {{
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
			  	end();
			  	createSkript();
			endAll();
			done();
		}};
	    return writer.getBuffer().toString();
	}

	private String getAddForwardingRule() {
		StringWriter writer = new StringWriter(); 
		new HtmlBuilder(writer,router) {{
			html();
			  createHead("Membrane Administrator - Add new rule");
			  body();
			  	h1().text("Add New Rule");
			  	form().action("/");
			  		table();
			  			tr().td().text("Name").end().td().input().type("text").name("name").end(3);
			  			tr().td().text("Port").end().td().input().type("text").name("port").end(3);
			  			tr().td().text("Target Host").end().td().input().type("text").name("targetHost").end(3);
			  			tr().td().text("Target Port").end().td().input().type("text").name("targetPort").end(3);
			  		end();
			  		input().value("Add").type("submit").classAttr("mb-button").end();
			  	end();
			  	createSkript();
			endAll();
			done();
		}};
		
		return writer.toString();
	}

	private Rule findRuleByName(String name) {
		List<Rule> rules = router.getRuleManager().getRules();
		for (Rule rule : rules) {
			if ( name.equals(rule.toString())) return rule;
		}
		return null;
	}
		
	
	private Header createHeader() {
		Header header = new Header();
		header.setContentType("text/html;charset=utf-8");
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		return header;
	}
	
}
