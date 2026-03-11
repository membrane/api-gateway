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
package com.predic8.membrane.core.interceptor.soap;

import com.googlecode.jatl.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.config.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.util.wsdl.parser.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.annot.Constants.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.regex.Pattern.*;

/**
 * @description Serves an HTML “web service explorer”.
 */
@MCElement(name="webServiceExplorer")
public class WebServiceExplorerInterceptor extends RESTInterceptor implements ProxyAware {

	private static final Logger log = LoggerFactory.getLogger(WebServiceExplorerInterceptor.class);

	private static final Pattern wsdlRequest = compile(".*\\?(wsdl|xsd=.*)", CASE_INSENSITIVE);

	private String wsdl;
	private String portName;

	/**
	 * Field is accessed by reflection
	 */
	private Proxy proxy;

	public WebServiceExplorerInterceptor() {
		name = "web service explorer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) {
		if (exc.getRequest().getMethod().equals("GET"))
			if (!isWSDLRequest(exc.getRequest()))
				return super.handleRequest(exc);

		return CONTINUE;
	}

	private boolean isWSDLRequest(Request request) {
		return wsdlRequest.matcher(request.getUri()).matches();
	}

	public String getWsdl() {
		return wsdl;
	}

	@Required
	@MCAttribute
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
	}

	public String getPortName() {
		return portName;
	}

	@MCAttribute
	public void setPortName(String portName) {
		this.portName = portName;
	}

	private String getClientURL(Exchange exc) {
		// TODO: move this to some central location
		try {
			var uri = exc.getHandler().getContextPath(exc) + exc.getRequest().getUri();
			var host = exc.getRequest().getHeader().getHost();
			if (host != null) {
				if (host.contains(":"))
					host = host.substring(0, host.indexOf(":"));

				uri = new URL(exc.getProxy().getProtocol(), host, exc.getHandler().getLocalPort(), uri).toString();
			}
			return uri;
		} catch (MalformedURLException e) {
			log.debug("Malformed URL", e);
			return exc.getRequest().getUri();
		}
	}

	/**
	 * Do not remove! Method is called dynamically by @Mapping!
	 */
	@Mapping("(?!.*operation)([^?]*)")
	public Response createSOAPUIResponse(QueryParameter params, final String relativeRootPath, final Exchange exc) throws Exception {
		try {
			var definitions = Definitions.parse(router.getResolverMap(), wsdl);

			var service = definitions.getServices().getFirst(); // TODO display all services
			var port = service.getPorts().getFirst();
			var ports = service.getPorts();

			var sw = new StringWriter();
			new StandardPage(sw, service.getName()) {
				@Override
				protected void createContent() {
					h1().text("Service Proxy: " + service.getName()).end();
					p();
					text("Target Namespace: " + definitions.getTargetNamespace());
					br().end();
					String wsdlLink = getClientURL(exc) + "?wsdl";
					text("WSDL: ").a().href(wsdlLink).text(wsdlLink).end();
					end();

					for (com.predic8.membrane.core.util.wsdl.parser.PortType pt : ports.stream().map(Port::getBinding).map(Binding::getPortType).toList()) {
						h2().text("Port Type: " + pt.getName()).end();
//						Documentation d = pt.getDocumentation(); @TODO
//						if (d != null) {
//							p().text("Documentation: " + d).end();
//						}
					}

					var binding = port.getBinding();
                    createOperationsTable(definitions, binding, binding.getPortType());

					h2().text("Virtual Endpoint").end();
					p().a().href(getClientURL(exc)).text(getClientURL(exc)).end().end();

					h2().text("Target Endpoints").end();
					if (service.getPorts().isEmpty())
						p().text("There are no endpoints defined.").end();
					else
						createEndpointTable(service.getPorts(), ports);
				}

				private void createOperationsTable(Definitions defs, Binding binding, PortType portType) {
					table().cellspacing("0").cellpadding("0").border(""+1);
					tr();
						th().text("Operation").end();
						th().text("Input").end();
						th().text("Output").end();
						th().text("Fault").end();
					end();
					for (Operation o : portType.getOperations()) {
						tr();
						td();
							text(o.getName());
						end();
						td();
						for (Part p : o.getInputs().stream().map(om -> om.getMessage().getPart()).toList())
							text(p.getElementQName().toString());
						end();
						td();
						for (Part p : o.getOutputs().stream().map(om -> om.getMessage().getPart()).toList())
							text(p.getElementQName().toString());
						end();
						td();
						for (Part p : o.getFaults().stream().map(om -> om.getMessage().getPart()).toList())
							text(p.getElementQName().toString());
						end();
						end();
					}
					end();
				}

				private void createEndpointTable(List<Port> ports, List<Port> matchingPorts) {
					table().cellspacing("0").cellpadding("0").border(""+1);
					tr();
					th().text("Port Name").end();
					th().text("Protocol").end();
					th().text("URL").end();
					end();
					for (com.predic8.membrane.core.util.wsdl.parser.Port p : ports) {
						tr();
						td().text(p.getName()).end();
						td().text(p.getBinding().getSoapVersion().name()).end();
						td().text(p.getAddress().getLocation()).end();
						td();
						if (matchingPorts.contains(p))
							text("*");
						end();
						end();
					}
					end();
					p().small().text("* available through this proxy").end().end();
				}

			};
			return ok(sw.toString()).build();
		} catch (IllegalArgumentException e) {
			log.error("", e);
			return Response.internalServerError().build();
		}
	}

	private abstract static class StandardPage extends Html {

		public StandardPage(Writer writer, String title) {
			super(writer);

			html();
			head();
			title().text(PRODUCT_NAME + (title == null ? "" : ": " + title)).end();
			style();
			raw("""
					<!--\r
					body { font-family: sans-serif; }\r
					h1 { font-size: 24pt; }\r
					h2 { font-size: 16pt; }\r
					h3 { font-size: 12pt; }\r
					td, th { border: 1px solid black; padding: 0pt 10pt; }\r
					table { border-collapse: collapse; }\r
					.help { margin-top:20pt; color:#AAAAAA; padding:1em 0em 0em 0em; font-size:10pt; }\r
					.footer { color:#AAAAAA; padding:0em 0em; font-size:10pt; }\r
					.footer a { color:#AAAAAA; }\r
					.footer a:hover { color:#000000; }\r
					-->""");
			end();
			end();
			body();
			createContent();
			p().classAttr("footer").raw(HTML_FOOTER).end();
			end();
			end();
		}

		protected abstract void createContent();
	}

	@Override
	public String getShortDescription() {
		return "Displays a graphical UI describing the web service when accessed using GET requests.";
	}

	@Override
	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}
}
