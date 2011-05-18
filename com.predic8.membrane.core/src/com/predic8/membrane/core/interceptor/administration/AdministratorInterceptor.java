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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ForwardingRule;
import com.predic8.membrane.core.rules.ProxyRule;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.util.HttpUtil;

public class AdministratorInterceptor extends AbstractInterceptor {

	private static Log log = LogFactory.getLog(AdministratorInterceptor.class.getName());
	
	private Pattern patternMain;

	private Pattern patternAddRule;

	public AdministratorInterceptor() {
		patternMain = Pattern.compile(".*/");
		patternAddRule = Pattern.compile(".*/add_forwarding_rule");
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (patternMain.matcher(exc.getOriginalRequestUri()).matches()) {
			exc.setResponse(createResponse(getMainPage()));
			return Outcome.ABORT;
		}

		else if (patternAddRule.matcher(exc.getOriginalRequestUri()).matches()) {
			exc.setResponse(createResponse(getAddForwardingRule()));
			return Outcome.ABORT;
		}
		
		log.debug("other pattern found: " + exc.getOriginalRequestUri());
		return Outcome.CONTINUE;
	}

	private Response createResponse(String body) {
		Response response = new Response();
		response.setStatusCode(200);
		response.setStatusMessage("OK");
		response.setHeader(createHeader());

		response.setBody(new Body(body));
		return response;
	}

	private String getMainPage() {
		StringBuffer buf = new StringBuffer();
		buf.append("<html>");
		buf.append("<head>");
		buf.append("<title>Membrane Administrator</title>");
		
		buf.append("</head>");

		buf.append("<body>");

		buf.append("<H2>Membrane Administration</H2>");

		long total = Runtime.getRuntime().totalMemory();
		long free = Runtime.getRuntime().freeMemory();

		buf.append("<p>Availabe system memory: " + total + "</p>");
		buf.append("<p>Free system memory: " + free + "</p>");

		buf.append("<H3>Rules</H3>");

		buf.append("<H4>Forwarding Rules: </H4>");

		buf.append("<table border='1'");

		buf.append("<tr>");
		buf.append("<th>Rule Name</th>");
		buf.append("<th>Listen Port</th>");
		buf.append("<th>Client Host</th>");
		buf.append("<th>Method</th>");
		buf.append("<th>Path</th>");
		buf.append("<th>Target Host</th>");
		buf.append("<th>Target Port</th>");
		buf.append("</tr>");

		List<Rule> rules = Router.getInstance().getRuleManager().getRules();
		for (Rule rule : rules) {
			if (!(rule instanceof ForwardingRule))
				continue;

			buf.append("<tr>");
			buf.append("<td>" + rule.toString() + "</td>");
			buf.append("<td>" + rule.getKey().getPort() + "</td>");
			buf.append("<td>" + rule.getKey().getHost() + "</td>");
			buf.append("<td>" + rule.getKey().getMethod() + "</td>");
			buf.append("<td>" + rule.getKey().getPath() + "</td>");
			buf.append("<td>" + ((ForwardingRule) rule).getTargetHost() + "</td>");
			buf.append("<td>" + ((ForwardingRule) rule).getTargetPort() + "</td>");
			buf.append("</tr>");
		}

		buf.append("<tr>");
		buf.append("<td>" + "<a href='add_forwarding_rule'>add new</a>" + "</td>");
		buf.append("<td>" + " " + "</td>");
		buf.append("<td>" + " " + "</td>");
		buf.append("<td>" + " " + "</td>");
		buf.append("<td>" + " " + "</td>");
		buf.append("<td>" + " " + "</td>");
		buf.append("<td>" + " " + "</td>");
		buf.append("</tr>");

		buf.append("</table>");

		buf.append("<H4>Proxy Rules: </H4>");

		buf.append("<table border='1'>");
		buf.append("<tr>");
		buf.append("<th>Rule Name</th>");
		buf.append("<th>Listen Port</th>");
		buf.append("</tr>");

		for (Rule rule : rules) {
			if (!(rule instanceof ProxyRule))
				continue;

			buf.append("<tr>");
			buf.append("<td>" + rule.toString() + "</td>");
			buf.append("<td>" + rule.getKey().getPort() + "</td>");
			buf.append("</tr>");
		}

		buf.append("</table>");

		buf.append("</body>");

		buf.append("</html>");
		return buf.toString();
	}

	private String getAddForwardingRule() {
		StringBuffer buf = new StringBuffer();
		buf.append("<html>");
		buf.append("<head>");
		buf.append("<title>Membrane Administrator: Add new rule</title>");
		buf.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"admin.css\" />");
		buf.append("</head>");

		buf.append("<body>");

		buf.append("<H2>Membrane Administration: Add new rule</H2>");

		buf.append("</body>");

		buf.append("</html>");
		return buf.toString();
	}

	protected String getCss() {

		InputStream in = getClass().getResourceAsStream("/configuration/admin.css");
		if (in == null)
			return "";

		StringBuffer buf = new StringBuffer();
		int c;
		try {
			while ((c = in.read()) >= 0) {
				buf.append((char) c);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return buf.toString();
	}

	private Header createHeader() {
		Header header = new Header();
		header.setContentType("text/html;charset=utf-8");
		header.add("Date", HttpUtil.GMT_DATE_FORMAT.format(new Date()));
		header.add("Server", "Membrane-Monitor " + Constants.VERSION);
		header.add("Connection", "close");
		return header;
	}

	protected String getCssContent() {
		StringBuffer buf = new StringBuffer();
		buf.append("<style type='text/css'");
	
		buf.append("body { background-color: ##E8E8E8; }");
		
		buf.append("\n");
		
		buf.append("H2 { color: #0033CC; margin-top: 30px; 	margin-left: 20px; 	}");
		
		buf.append("\n");
		
		buf.append("H3 { color: #0033CC; margin-top: 30px; 	margin-left: 20px; 	}");
		
		buf.append("\n");
		
		buf.append("H4 { color: #660000; margin-left: 40px;  }");
		
		buf.append("\n");
		
		buf.append("table { margin-left: 80px; }");
		
		buf.append("\n");
		
		buf.append("p { margin-left: 80px; }");
		
		buf.append("</style");	
		return buf.toString();
	}
	
}
