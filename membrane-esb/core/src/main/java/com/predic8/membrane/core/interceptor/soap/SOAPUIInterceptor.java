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

import groovy.xml.MarkupBuilder;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.administration.Mapping;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.interceptor.rest.RESTInterceptor;
import com.predic8.membrane.core.rules.SOAPProxy;
import com.predic8.wsdl.Binding;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Documentation;
import com.predic8.wsdl.Operation;
import com.predic8.wsdl.Part;
import com.predic8.wsdl.Port;
import com.predic8.wsdl.PortType;
import com.predic8.wsdl.Service;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;
import com.predic8.wstool.creator.RequestTemplateCreator;
import com.predic8.wstool.creator.SOARequestCreator;

public class SOAPUIInterceptor extends RESTInterceptor {
	
	private static Log log = LogFactory.getLog(SOAPUIInterceptor.class.getName());
	
	private String wsdl;
	private String portName;
	
	public SOAPUIInterceptor() {
		name = "SOAP UI";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().getMethod().equals("GET"))
			if (!isWSDLRequest(exc.getRequest()))
				return super.handleRequest(exc);

		return Outcome.CONTINUE;
	}

	private static final Pattern wsdlRequest = Pattern.compile(".*\\?(wsdl|xsd=.*)", Pattern.CASE_INSENSITIVE);
	
	private boolean isWSDLRequest(Request request) {
		return wsdlRequest.matcher(request.getUri()).matches();
	}

	public String getWsdl() {
		return wsdl;
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
		this.parsedWSDL = null;
	}
	
	public String getPortName() {
		return portName;
	}
	
	public void setPortName(String portName) {
		this.portName = portName;
	}
	
	private volatile Definitions parsedWSDL;

	private Definitions getParsedWSDL() {
		if (parsedWSDL != null)
			return parsedWSDL;
		WSDLParserContext ctx = new WSDLParserContext();
		ctx.setInput(wsdl);
		WSDLParser wsdlParser = new WSDLParser();
		wsdlParser.setResourceResolver(router.getResourceResolver().toExternalResolver());
		return parsedWSDL = wsdlParser.parse(ctx);
	}
	
	@Mapping("[^?]*/operation/([^/?]+)/([^/?]+)/([^/?]+)")
	public Response createOperationResponse(QueryParameter params, String relativeRootPath) throws Exception {
		try {
			final String bindingName = params.getGroup(1);
			final String portName = params.getGroup(2);
			final String operationName = params.getGroup(3);

			final Definitions w = getParsedWSDL();
			final Service service = getService(w);
			
			StringWriter sw = new StringWriter();
			new StandardPage(sw) {
				@Override
				protected void createContent() {
					h1().text("Service Proxy for " + service.getName());
					h2().text("Operation: " + operationName).end();
					
					h3().text("Sample Request").end();

				    pre().text(generateSampleRequest(portName, operationName, bindingName, w)).end();
				}
			};
			return Response.ok(sw.toString()).build();
		} catch (IllegalArgumentException e) {
			log.error("", e);
			return Response.interalServerError().build();
		}
	}
	
	private Service getService(Definitions d) {
		if (d.getServices().size() != 1)
			throw new IllegalArgumentException("WSDL needs to have exactly one service for SOAPUIInterceptor to work.");
		return d.getServices().get(0);
	}
	
	@Mapping("(?!.*operation)([^?]*)")
	public Response createSOAPUIResponse(QueryParameter params, final String relativeRootPath) throws Exception {
		try {
			final String myPath = params.getGroup(1);
			
			final Definitions w = getParsedWSDL();
			final Service service = getService(w);
			final Port port = SOAPProxy.selectPort(service.getPorts(), portName);
			final List<Port> ports = getPortsByLocation(service, port);
			
			StringWriter sw = new StringWriter();
			new StandardPage(sw) {
				@Override
				protected void createContent() {
					h1().text("Service Proxy").end();
					p();
						text("Service Name: " + service.getName());
						br().end();
						text("Target Namespace: " + w.getTargetNamespace());
						br().end();
						String wsdlLink = relativeRootPath +  myPath + "?wsdl";
						text("WSDL: ").a().href(wsdlLink).text(wsdlLink).end();
					end();
					
					for (Port port : ports) {
						h2().text("Port: " + port.getName()).end();
						p();
							text("Port Type: " + getPortType(port));
							br().end();
							
							Binding binding = port.getBinding();
							PortType portType = binding.getPortType();
							Documentation d = portType.getDocumentation();
							if (d != null)
								text("Documentation: " + d.toString());
						end();
						h3().text("Operations").end();
						
						List<Operation> bindingOperations = getOperationsByBinding(w, binding);
						
						if (bindingOperations.size() == 0)
							p().text("There are no operations defined.").end();
						else 
							createOperationsTable(w, bindingOperations, binding, portType);
					}
				}

				private void createOperationsTable(Definitions w, List<Operation> bindingOperations, Binding binding, PortType portType) {
					table().cellspacing("0").cellpadding("0").border(""+1);
						tr();
							th().text("Name").end();
							th().text("Input").end();
							th().text("Output").end();
						end();
						for (Operation o : bindingOperations) {
							tr();
								String link = relativeRootPath + myPath + "/operation/" + binding.getName() + "/" + portType.getName() + "/" + o.getName(); 
								td().a().href(link).text(o.getName()).end().end();
								td();
									for (Part p : o.getInput().getMessage().getParts())
										text(p.getElement());
									
								end();
								td();
									for (Part p : o.getOutput().getMessage().getParts())
										text(p.getElement());
								end();
							end();
						}
					end();
				}
	
			};
			return Response.ok(sw.toString()).build();
		} catch (IllegalArgumentException e) {
			log.error("", e);
			return Response.interalServerError().build();
		}
	}

	private abstract class StandardPage extends Html {

		public StandardPage(Writer writer) {
			super(writer);
			
			html();
				head();
					title().text(Constants.PRODUCT_NAME + ": Service Proxies").end();
					style();
					raw("<!--\r\n" +
						"body { font-family: sans-serif; }\r\n" +
						"h1 { font-size: 24pt; }\r\n" +
						"h2 { font-size: 16pt; }\r\n" +
						"h3 { font-size: 12pt; }\r\n" +
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
					createContent();
					p().classAttr("footer").raw(Constants.HTML_FOOTER).end();
				end();
			end();
		}
		
		protected abstract void createContent();
	}

	private List<Operation> getOperationsByBinding(final Definitions w, Binding binding) {
		List<Operation> bindingOperations = new ArrayList<Operation>();
		for (Operation o : w.getOperations())
			if (binding.getOperation(o.getName()) != null)
				bindingOperations.add(o);
		return bindingOperations;
	}

	private List<Port> getPortsByLocation(Service service, Port port) {
		String location = port.getAddress().getLocation();
		if (location == null)
			throw new IllegalArgumentException("Location not set for port in WSDL.");

		final List<Port> ports = new ArrayList<Port>();
		for (Port p : service.getPorts())
			if (location.equals(p.getAddress().getLocation()))
				ports.add(p);
		return ports;
	}
	
	private String getPortType(Port port) {
		String transport = (String) port.getBinding().getBinding().getElementName().getNamespaceURI();
		if (Constants.WSDL_SOAP11_NS.equals(transport))
			transport = "SOAP 1.1";
		if (Constants.WSDL_SOAP12_NS.equals(transport))
			transport = "SOAP 1.2";
		return transport;
	}

	private String generateSampleRequest(final String portName, final String operationName,
			final String bindingName, final Definitions w) {
		StringWriter writer = new StringWriter();
	    SOARequestCreator creator = new SOARequestCreator();
	    creator.setBuilder(new MarkupBuilder(writer));
	    creator.setDefinitions(w);
	    creator.setCreator(new RequestTemplateCreator());
	    creator.createRequest(portName, operationName, bindingName);
		return writer.toString();
	}

	@Override
	public String getShortDescription() {
		return "Displays a graphical UI describing the web service when accessed using GET requests.";
	}
}
