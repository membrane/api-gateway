package com.predic8.membrane.core.interceptor.groovy;

import javax.xml.stream.*;

import groovy.lang.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.*;

public class GroovyInterceptor extends AbstractInterceptor {
	
	private String src = "";

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		return runScript(exc);
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		return runScript(exc);
	}
	
	private Outcome runScript(Exchange exc) {
		Binding b = new Binding();
		b.setVariable("exc", exc);
		GroovyShell shell = new GroovyShell(b);
		return (Outcome)shell.evaluate(srcWithImports());
	}

	private String srcWithImports() {
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
	


	
}
