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

import java.io.StringWriter;
import java.util.List;

import com.googlecode.jatl.Html;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.wsdl.Definitions;
import com.predic8.wsdl.Operation;
import com.predic8.wsdl.Part;
import com.predic8.wsdl.WSDLParser;
import com.predic8.wsdl.WSDLParserContext;

public class SOAPUIInterceptor extends AbstractInterceptor {
	
	private String wsdl;
	
	public SOAPUIInterceptor() {
		name = "SOAP UI";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		if (exc.getRequest().getMethod().equals("GET")) {
			
			exc.setResponse(createSOAPUIResponse());
			
			return Outcome.RETURN;
		}
		return Outcome.CONTINUE;
	}

	public String getWsdl() {
		return wsdl;
	}
	
	public void setWsdl(String wsdl) {
		this.wsdl = wsdl;
		this.parsedWSDL = null;
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
	
	private Response createSOAPUIResponse() throws Exception {
		final Definitions w = getParsedWSDL();
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
							"h2 { font-size: 16pt; }\r\n" +
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
						h1().text("Service Proxy").end();
						p().text("Service Name: " + w.getName()).end();
						h2().text("Operations").end();
						List<Operation> operations = w.getOperations();
						if (operations.size() == 0)
							p().text("There are no operations defined.").end();
						else 
							createOperationsTable(operations);
						p().classAttr("footer").raw(Constants.HTML_FOOTER).end();
					end();
				end();
			}
		
			private void createOperationsTable(List<Operation> operations) {
				table().cellspacing("0").cellpadding("0").border(""+1);
					tr();
						th().text("Name").end();
						th().text("Input").end();
						th().text("Output").end();
					end();
					for (Operation o : operations) {
						tr();
							td().text(o.getName()).end();
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
	}

	@Override
	public String getShortDescription() {
		return "Displays a graphical UI describing the web service when accessed using GET requests.";
	}
}
