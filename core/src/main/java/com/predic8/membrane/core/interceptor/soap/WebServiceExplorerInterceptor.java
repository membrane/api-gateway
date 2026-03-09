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
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
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
	private Proxy proxy;

	private com.predic8.membrane.core.util.wsdl.parser.Definitions definitions;

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
			String uri = exc.getHandler().getContextPath(exc) + exc.getRequest().getUri();
			String host = exc.getRequest().getHeader().getHost();
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
			final String myPath = router.getConfiguration().getUriFactory().create(exc.getRequest().getUri()).getPath();

			final com.predic8.membrane.core.util.wsdl.parser.Definitions w = getDefinitions();

			final com.predic8.membrane.core.util.wsdl.parser.Service service = w.getServices().getFirst(); // TODO display all services
			final com.predic8.membrane.core.util.wsdl.parser.Port port = service.getPorts().getFirst();
			final List<com.predic8.membrane.core.util.wsdl.parser.Port> ports = service.getPorts();

			var sw = new StringWriter();
			new StandardPage(sw, service.getName()) {
				@Override
				protected void createContent() {
					h1().text("Service Proxy: " + service.getName()).end();
					p();
					text("Target Namespace: " + w.getTargetNamespace());
					br().end();
					String wsdlLink = getClientURL(exc) + "?wsdl";
					text("WSDL: ").a().href(wsdlLink).text(wsdlLink).end();
					end();

					for (com.predic8.membrane.core.util.wsdl.parser.PortType pt : ports.stream().map(port1 -> port1.getBinding()).map(b -> b.getPortType()).toList()) {
						h2().text("Port Type: " + pt.getName()).end();
//						Documentation d = pt.getDocumentation();
//						if (d != null) {
//							p().text("Documentation: " + d).end();
//						}
					}

					com.predic8.membrane.core.util.wsdl.parser.Binding binding = port.getBinding();
                    createOperationsTable(definitions, binding, binding.getPortType());

					h2().text("Virtual Endpoint").end();
					p().a().href(getClientURL(exc)).text(getClientURL(exc)).end().end();

					h2().text("Target Endpoints").end();
					if (service.getPorts().isEmpty())
						p().text("There are no endpoints defined.").end();
					else
						createEndpointTable(service.getPorts(), ports);
				}

				private void createOperationsTable(com.predic8.membrane.core.util.wsdl.parser.Definitions w, com.predic8.membrane.core.util.wsdl.parser.Binding binding, com.predic8.membrane.core.util.wsdl.parser.PortType portType) {
					table().cellspacing("0").cellpadding("0").border(""+1);
					tr();
						th().text("Operation").end();
						th().text("Input").end();
						th().text("Output").end();
					end();
					for (com.predic8.membrane.core.util.wsdl.parser.Operation o : portType.getOperations()) {
						tr();
						td();
							text(o.getName());
						end();
						td();
						for (com.predic8.membrane.core.util.wsdl.parser.Part p : o.getInputs().stream().map(i -> i.getPart()).toList())
							text(p.getElementQName().toString());
						end();
						td();
						for (com.predic8.membrane.core.util.wsdl.parser.Part p : o.getOutputs().stream().map(i -> i.getPart()).toList())
							text(p.getElementQName().toString());
						end();
						end();
					}
					end();
				}

				private void createEndpointTable(List<com.predic8.membrane.core.util.wsdl.parser.Port> ports, List<com.predic8.membrane.core.util.wsdl.parser.Port> matchingPorts) {
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

	private com.predic8.membrane.core.util.wsdl.parser.Definitions getDefinitions() throws Exception {
		return com.predic8.membrane.core.util.wsdl.parser.Definitions.parse(router.getResolverMap(),wsdl);
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
