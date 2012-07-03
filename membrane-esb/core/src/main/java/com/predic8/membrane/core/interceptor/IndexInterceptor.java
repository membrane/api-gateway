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
package com.predic8.membrane.core.interceptor;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.lang.StringUtils;
import org.springframework.web.util.HtmlUtils;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HostColonPort;
import com.predic8.membrane.core.transport.http.HttpServerHandler;

public class IndexInterceptor extends AbstractInterceptor {
	
	private static class ServiceInfo {
		public String name, url, host, port, path;
		public boolean ssl;
		public String method;
	}

	public List<ServiceInfo> getServices(Exchange exc) {
		List<ServiceInfo> result = new ArrayList<ServiceInfo>();
		for (Rule r : router.getRuleManager().getRules()) {
			if (r instanceof ServiceProxy) {
				ServiceInfo si = getServiceInfo(exc, (ServiceProxy)r);
				if (si != null)
					result.add(si);
			}
		}
		return result;
	}
	
	private ServiceInfo getServiceInfo(Exchange exc, ServiceProxy sp) {
		if (sp.getInterceptors().size() == 1 && sp.getInterceptors().get(0) instanceof IndexInterceptor)
			return null;
		
		ServiceProxyKey k = (ServiceProxyKey) sp.getKey();
		
		ServiceInfo ri = new ServiceInfo();
		
		ri.method = k.getMethod();
		
		ri.ssl = sp.getSslInboundContext() != null;// NOTE: when running as servlet, we have no idea what the protocol was
		String protocol = ri.ssl ? "https" : "http";
		
		String host = k.isHostWildcard() ? new HostColonPort(exc.getRequest().getHeader().getHost()).host : k.getHost();
		if (host == null)
			host = "localhost"; // TODO: use local IP address (which is not exposed by the transport at the moment)
		
		int port = k.getPort();
		if (port == -1 || !exc.getHandler().isMatchLocalPort())
			port = exc.getHandler().getLocalPort();
		
		String path;
		if (!k.isUsePathPattern()) {
			path = "/";
		} else if (k.isPathRegExp()) {
			path = fullfillRegexp(k.getPath());
		} else {
			path = "/" + StringUtils.removeStart(k.getPath(), "/");
		}
		
		if (!"".equals(getRouter().getTransport().getContextPath(exc))) {
			path = StringUtils.removeEnd(getRouter().getTransport().getContextPath(exc), "/")  + "/" + StringUtils.removeStart(path, "/");
		}
		
		ri.name = sp.getName();
		if (path != null)
			ri.url = protocol + "://" + host + ":" + port + path;
		ri.host = k.isHostWildcard() ? "" : HtmlUtils.htmlEscape(k.getHost());
		ri.port = k.getPort() == -1 ? "" : "" + k.getPort();
		ri.path = k.isUsePathPattern() ? "<tt>" + HtmlUtils.htmlEscape(k.getPath()) + "</tt>" + (k.isPathRegExp() ? " (regex)" : "") : ""; 
		return ri;
	}
	
	private String fullfillRegexp(String path) {
		StringBuilder sb = new StringBuilder();
		int p = 0;
		while (p < path.length()) {
			int c = path.codePointAt(p++);
			switch (c) {
			case '\\':
				if (p == path.length())
					return null; // illegal
				c = path.codePointAt(p++);
				if (Character.isDigit(c))
					return null; // backreferences are not supported
				if (c == 'Q') {
					while (true) {
						if (p == path.length())
							return null; // 'end of regex' within quote
						c = path.codePointAt(p++);
						if (c == '\\') {
							if (p == path.length())
								return null; // 'end of regex' within quote
							c = path.codePointAt(p++);
							if (c == 'E')
								break;
							sb.append('\\');
						}
						sb.appendCodePoint(c);
					}
					break;
				}
				if (c == 'E') {
					return null; // 'end of quote' without begin
				}
				sb.appendCodePoint(c);
				break;
			case '[':
			case '?':
			case '*':
			case '+':
			case '{':
			case '(':
				return null; // meaningful characters we do not unterstand
			case '|':
				break;
			case '^':
				if (p == 1)
					break;
				return null;
			case '$':
				if (p == path.length() || path.codePointAt(p) == '|')
					break;
				return null;
			case '.':
				int q; 
				if (p != path.length() && isQuantifier(q = path.codePointAt(p))) {
					if (++p != path.length() && isModifier(path.codePointAt(p)))
						p++;
					if (q == '+')
						sb.append('a');
				} else {
					sb.append('a');
				}
				break;
			default:
				sb.appendCodePoint(c);
				break;
			}
		}
		return sb.toString();
	}
	
	private boolean isQuantifier(int c) {
		return c == '?' || c == '*' || c == '+';
	}

	private boolean isModifier(int c) {
		return c == '?' || c == '+';
	}

	@Override
	public Outcome handleRequest(final Exchange exc) throws Exception {
		StringWriter sw = new StringWriter();
		new Html(sw) {
			{
				html();
					head();
						title().text(Constants.PRODUCT_NAME + ": Service Proxies").end();
						style();
						raw("<!--\r\n" +
							"body { font-family: sans-serif; }\r\n" +
							"h1 { font-size: 24pt; }\r\n" +
							"td, th { border: 1px solid black; padding: 0pt 10pt; }\r\n" +
							"table { border-collapse: collapse; }\r\n" +
							".help { margin-top:20pt; color:#AAAAAA; padding:1em 0em 0em 0em; font-size:10pt; }\r\n" + 
							".footer { color:#AAAAAA; padding:0em 0em; font-size:10pt; }\r\n" + 
							".footer a { color:#AAAAAA; text-decoration: none; }\r\n" + 
							".footer a:hover { text-decoration: underline; }\r\n" + 
							"-->");
						end();
					end();
					body();
						h1().text("Service Proxies").end();
						List<ServiceInfo> services = getServices(exc);
						if (services.size() == 0)
							p().text("There are no services defined.").end();
						else 
							createIndexTable(services, exc.getHandler() instanceof HttpServerHandler);
						p().classAttr("help").text("The hyperlinks might not work due to semantics which is not known to " + Constants.PRODUCT_NAME + ".").end();
						p().classAttr("footer").raw(Constants.HTML_FOOTER).end();
					end();
				end();
			}
		
			private void createIndexTable(List<ServiceInfo> services, boolean showSSL) {
				table().cellspacing("0").cellpadding("0").border(""+1);
					tr();
						th().text("Name").end();
						th().text("Virtual Host").end();
						th().text("Port").end();
						th().text("Path").end();
						if (showSSL)
							th().text("SSL").end();
					end();
					for (ServiceInfo ri : services) {
						tr();
							td();
								if (ri.url != null && !"POST".equals(ri.method)) {
									a().href(ri.url);
									text(ri.name);
									end();
								} else {
									text(ri.name);									
								}
							end();
							td().raw(ri.host).end();
							td().raw(ri.port).end();
							td().raw(ri.path).end();
							if (showSSL)
								td().raw(ri.ssl ? "yes" : "").end();
						end();
					}
				end();
			}
		};
		exc.setResponse(Response.ok(sw.toString()).build());
		return Outcome.RETURN;
	}

	@Override
	public String getShortDescription() {
		return "Lists services available through the " + Constants.PRODUCT_NAME + " service proxies.";
	}
	
	@Override
	public String getHelpId() {
		return "index-interceptor";
	}
	
	protected void writeInterceptor(XMLStreamWriter out) throws XMLStreamException {
		out.writeStartElement("index");
		out.writeEndElement();
	}


}
