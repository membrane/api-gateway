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

package com.predic8.membrane.core.interceptor.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.util.HtmlUtils;

import com.predic8.membrane.annot.MCInterceptor;
import com.predic8.membrane.core.config.XMLElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TextUtil;

@MCInterceptor(xsd="" +
		"	<xsd:element name=\"groovy\">\r\n" + 
		"		<xsd:complexType>\r\n" + 
		"			<xsd:complexContent mixed=\"true\">\r\n" + 
		"				<xsd:extension base=\"beans:identifiedType\">\r\n" + 
		"					<xsd:sequence />\r\n" + 
		"				</xsd:extension>\r\n" + 
		"			</xsd:complexContent>\r\n" + 
		"		</xsd:complexType>\r\n" + 
		"	</xsd:element>\r\n" + 
		"")
public class GroovyInterceptor extends AbstractInterceptor {
	private static final Log log = LogFactory.getLog(GroovyInterceptor.class);
	private static final GroovyShell shell = new GroovyShell();
	private final int concurrency = Runtime.getRuntime().availableProcessors() * 2;

	private String src = "";
	private ArrayBlockingQueue<Script> scripts;

	public GroovyInterceptor() {
		name = "Groovy";
	}
	
	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return runScript(exc);
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return runScript(exc);
	}

	
	private void createOneScript(ArrayBlockingQueue<Script> scripts, String src) throws InterruptedException {
		Script s;
		synchronized (shell) {
			s = shell.parse(srcWithImports(src));
		}
		scripts.put(s);
	}
	
	public void init() {
		if (router == null)
			return;
		if ("".equals(src))
			return;
		
		scripts = new ArrayBlockingQueue<Script>(concurrency);
		try {
			createOneScript(scripts, src);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		router.getBackgroundInitializator().execute(new Runnable() {
			// close over 'scripts' and 'src' since their value might change while we run
			private ArrayBlockingQueue<Script> scripts = GroovyInterceptor.this.scripts;
			private String src = GroovyInterceptor.this.src;
			
			@Override
			public void run() {
				try {
					for (int i = 1; i < concurrency; i++)
						createOneScript(scripts, src);
				} catch (Exception e) {
					log.error("Error creating Groovy Script:", e);
				}
			}
		});

	}
	
	private Outcome runScript(Exchange exc) throws InterruptedException {
		Binding b = new Binding();
		b.setVariable("exc", exc);
		Script s = scripts.take();
		try {
			s.setBinding(b);
			return getOutcome(s.run(), exc);
		} finally {
			scripts.put(s);
		}
	}

	private Outcome getOutcome(Object res, Exchange exc) {
		if (res instanceof Outcome) {
			return (Outcome) res;
		}
		
		if (res instanceof Response) {
			exc.setResponse((Response)res);
			return Outcome.RETURN;
		}
		
		if (res instanceof Request) {
			exc.setRequest((Request) res);
		}
		return Outcome.CONTINUE;
	}

	private String srcWithImports(String src) {
		return "import static com.predic8.membrane.core.interceptor.Outcome.*\nimport com.predic8.membrane.core.http.*\n"+src;
	}		
	
	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {
		out.writeStartElement("groovy");
		out.writeCharacters(src);		
		out.writeEndElement();
	}
	
	@Override
	public XMLElement parse(XMLStreamReader token) throws Exception {
		src = "";
		return super.parse(token);
	}
	
	@Override
	protected void parseCharacters(XMLStreamReader token)
			throws XMLStreamException {
		src += token.getText();
	}
	
	public String getSrc() {
		return src;
	}

	public void setSrc(String src) {
		this.src = src;
	}
	
	@Override
	public String getShortDescription() {
		return "Executes a groovy script.";
	}
	
	@Override
	public String getLongDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(TextUtil.removeFinalChar(getShortDescription()));
		sb.append(":<br/><pre style=\"overflow-x:auto\">");
		sb.append(HtmlUtils.htmlEscape(TextUtil.removeCommonLeadingIndentation(src)));
		sb.append("</pre>");
		return sb.toString();
	}
	
	@Override
	public String getHelpId() {
		return "groovy";
	}
	
}
