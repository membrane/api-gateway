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

import javax.xml.stream.*;

import groovy.lang.*;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
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
		return getOutcome(shell.evaluate(srcWithImports()), exc);
	}

	private Outcome getOutcome(Object res, Exchange exc) {
		if (res instanceof Outcome) {
			return (Outcome) res;
		}
		
		if (res instanceof Response) {
			exc.setResponse((Response)res);
			return Outcome.ABORT;
		}
		
		if (res instanceof Request) {
			exc.setRequest((Request) res);
		}
		return Outcome.CONTINUE;
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
