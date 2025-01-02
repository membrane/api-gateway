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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.rules.*;
import com.predic8.membrane.core.util.*;
import com.predic8.wsdl.*;
import com.predic8.wstool.creator.*;
import groovy.xml.MarkupBuilder;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.regex.Pattern.*;

@MCElement(name="webServiceExplorer")
public class WebServiceExplorerInterceptor extends RESTInterceptor {

	private static final Logger log = LoggerFactory.getLogger(WebServiceExplorerInterceptor.class.getName());

	private static final Pattern wsdlRequest = compile(".*\\?(wsdl|xsd=.*)", CASE_INSENSITIVE);

	private String wsdl;
	private String portName;

	public WebServiceExplorerInterceptor() {
		name = "Web Service Explorer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
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
		this.parsedWSDL = null;
	}

	public String getPortName() {
		return portName;
	}

	@MCAttribute
	public void setPortName(String portName) {
		this.portName = portName;
	}

	private volatile Definitions parsedWSDL;

	private Definitions getParsedWSDL() {
		if (parsedWSDL != null)
			return parsedWSDL;
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(ResolverMap.combine(router.getBaseLocation(), wsdl));
        return parsedWSDL = getWsdlParser().parse(ctx);
	}

	private @NotNull WSDLParser getWsdlParser() {
		WSDLParser wsdlParser = new WSDLParser();
		wsdlParser.setResourceResolver(router.getResolverMap().toExternalResolver().toExternalResolver());
		return wsdlParser;
	}

	@Mapping("[^?]*/operation/([^/?]+)/([^/?]+)/([^/?]+)")
	public Response createOperationResponse(QueryParameter params, String relativeRootPath) throws Exception {
		try {
			final String bindingName = params.getGroup(1);
			final String portName = params.getGroup(2);
			final String operationName = params.getGroup(3);

            final Service service = getService(getParsedWSDL());

			StringWriter sw = new StringWriter();
			new StandardPage(sw, null) {
				@Override
				protected void createContent() {
					h1().text("Service Proxy for " + service.getName());
					h2().text("Operation: " + operationName).end();

					h3().text("Sample Request").end();

					pre().text(generateSampleRequest(portName, operationName, bindingName, getParsedWSDL())).end();
				}
			};
			return ok(sw.toString()).build();
		} catch (IllegalArgumentException e) {
			log.error("", e);
			return Response.internalServerError().build();
		}
	}

	private Service getService(Definitions d) {
		
		if (getProxy() instanceof SOAPProxy sp) {
			String serviceName = sp.getServiceName();
			System.out.println("serviceName = " + serviceName);
			if (serviceName != null) {
				return WSDLUtil.getService(d, serviceName);
			}
		}
		
		if (d.getServices().size() != 1)
			throw new IllegalArgumentException("WSDL needs to have exactly one service for SOAPUIInterceptor to work.");
		return d.getServices().getFirst();
	}

	private String getClientURL(Exchange exc) {
		// TODO: move this to some central location
		try {
			String uri = exc.getHandler().getContextPath(exc) + exc.getRequestURI();
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

	@Mapping("(?!.*operation)([^?]*)")
	public Response createSOAPUIResponse(QueryParameter params, final String relativeRootPath, final Exchange exc) throws Exception {
		try {
			final String myPath = router.getUriFactory().create(exc.getRequestURI()).getPath();

			final Definitions w = getParsedWSDL();
			final Service service = getService(w);
			final Port port = SOAPProxy.selectPort(service.getPorts(), portName);
			final List<Port> ports = getPortsByLocation(service, port);

			StringWriter sw = new StringWriter();
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

					for (PortType pt : WSDLUtil.getPortTypes(service)) {
						h2().text("Port Type: " + pt.getName()).end();
						Documentation d = pt.getDocumentation();
						if (d != null) {
							p().text("Documentation: " + d).end();
						}
					}

					Binding binding = port.getBinding();
					List<Operation> bindingOperations = getOperationsByBinding(w, binding);
					if (bindingOperations.isEmpty())
						p().text("There are no operations defined.").end();
					else
						createOperationsTable(w, bindingOperations, binding, binding.getPortType());

					h2().text("Virtual Endpoint").end();
					p().a().href(getClientURL(exc)).text(getClientURL(exc)).end().end();

					h2().text("Target Endpoints").end();
					if (service.getPorts().isEmpty())
						p().text("There are no endpoints defined.").end();
					else
						createEndpointTable(service.getPorts(), ports);
				}

				private void createOperationsTable(Definitions w, List<Operation> bindingOperations, Binding binding, PortType portType) {
					table().cellspacing("0").cellpadding("0").border(""+1);
					tr();
					th().text("Operation").end();
					th().text("Input").end();
					th().text("Output").end();
					end();
					for (Operation o : bindingOperations) {
						tr();
						td();
						if ("HTTP".equals(getProtocolVersion(binding))) {
							text(o.getName());
						} else {
							a().href(getLinkForOperation(binding, portType, o, myPath)).text(o.getName()).end();
						}
						end();
						td();
						for (Part p : o.getInput().getMessage().getParts())
							text(p.getElement().getName());

						end();
						td();
						for (Part p : o.getOutput().getMessage().getParts())
							text(p.getElement().getName());
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
					for (Port p : ports) {
						tr();
						td().text(p.getName()).end();
						td().text(getProtocolVersion(p.getBinding())).end();
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

	private String getLinkForOperation(Binding binding, PortType portType, Operation o, String path) {
		return path + "/operation/" + binding.getName() + "/" + portType.getName() + "/" + o.getName();
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

	private List<Operation> getOperationsByBinding(final Definitions w, Binding binding) {
		List<Operation> bindingOperations = new ArrayList<>();
		for (Operation o : w.getOperations())
			if (binding.getOperation(o.getName()) != null)
				bindingOperations.add(o);
		return bindingOperations;
	}

	private List<Port> getPortsByLocation(Service service, Port port) {
		String location = port.getAddress().getLocation();
		if (location == null)
			throw new IllegalArgumentException("Location not set for port in WSDL.");

		final List<Port> ports = new ArrayList<>();
		for (Port p : service.getPorts())
			if (location.equals(p.getAddress().getLocation()))
				ports.add(p);
		return ports;
	}

	private String getProtocolVersion(Binding binding) {
		String transport = getNamespaceURI(binding);
		if (WSDL_SOAP11_NS.equals(transport))
			transport = "SOAP 1.1";
		if (WSDL_SOAP12_NS.equals(transport))
			transport = "SOAP 1.2";
		if (WSDL_HTTP_NS.equals(transport))
			transport = "HTTP";
		return transport;
	}

	private String getNamespaceURI(Binding binding) {
		return ((javax.xml.namespace.QName) binding.getBinding().getElementName()).getNamespaceURI();
	}

	private String generateSampleRequest(final String portName, final String operationName,
			final String bindingName, final Definitions w) {
		StringWriter writer = new StringWriter();
		SOARequestCreator creator = new SOARequestCreator(w, new RequestTemplateCreator(), new MarkupBuilder(writer));
		creator.createRequest(portName, operationName, bindingName);
		return writer.toString();
	}

	@Override
	public String getShortDescription() {
		return "Displays a graphical UI describing the web service when accessed using GET requests.";
	}
}
