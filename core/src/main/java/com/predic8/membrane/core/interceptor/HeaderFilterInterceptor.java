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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.Logger;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;

@MCElement(name="headerFilter", xsd="" +
		"					<xsd:sequence maxOccurs=\"unbounded\">\r\n" + 
		"						<xsd:choice>\r\n" + 
		"							<xsd:element name=\"include\" type=\"xsd:string\" />\r\n" + 
		"							<xsd:element name=\"exclude\" type=\"xsd:string\" />\r\n" + 
		"						</xsd:choice>\r\n" + 
		"					</xsd:sequence>\r\n" + 
		"", generateParserClass=false)
public class HeaderFilterInterceptor extends AbstractInterceptor {

	private static final Logger log = Logger.getLogger(HeaderFilterInterceptor.class);
	
	private List<Rule> rules = new ArrayList<Rule>();
	
	public HeaderFilterInterceptor() {
		name = "Header Filter";
	}

	public enum Action { KEEP, REMOVE }
	
	public static class Rule {
		private final String pattern;
		private final Pattern p;
		private final Action action;
		
		public Rule(String pattern, Action action) {
			this.pattern = pattern;
			p = Pattern.compile(pattern);
			this.action = action;
		}
		
		public boolean matches(String header) {
			return p.matcher(header).matches();
		}
		
		public Action getAction() {
			return action;
		}
		
		protected void write(XMLStreamWriter out)
				throws XMLStreamException {
			out.writeStartElement(getClass().getSimpleName().toLowerCase());
			out.writeCharacters(pattern);
			out.writeEndElement();
		}
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		handleMessage(exc.getResponse());
		return Outcome.CONTINUE;
	}
	
	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		handleMessage(exc.getResponse());
		return Outcome.CONTINUE;
	}
	
	@Override
	public void handleAbort(Exchange exchange) {
		handleMessage(exchange.getResponse());
	}
	
	private void handleMessage(Message msg) {
		if (msg == null)
			return;
		Header h = msg.getHeader();
		if (h == null)
			return;
		for (HeaderField hf : h.getAllHeaderFields())
			for (Rule r : rules)
				if (r.matches(hf.getHeaderName().toString())) {
					switch (r.getAction()) {
					case REMOVE:
						log.debug("Removing HTTP header " + hf.getHeaderName().toString());
						h.remove(hf);
						break;
					case KEEP:
						break;
					}
					break;
				}
	}

	public List<Rule> getRules() {
		return rules;
	}
	
	public void setRules(List<Rule> rules) {
		this.rules = rules;
	}
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("headerFilter");
		for (Rule r : rules)
			r.write(out);
		out.writeEndElement();
	}
	
	@Override
	protected void parseAttributes(XMLStreamReader token) throws Exception {
		rules.clear();
	}

	@Override
	protected void parseChildren(XMLStreamReader token, String child)
			throws Exception {
		if (token.getLocalName().equals("include"))
			rules.add(new Rule(token.getElementText(), Action.KEEP));
		else if (token.getLocalName().equals("exclude"))
			rules.add(new Rule(token.getElementText(), Action.REMOVE));
		else
			super.parseChildren(token, child);
	}
	
	@Override
	public String getHelpId() {
		return "header-filter";
	}

}
