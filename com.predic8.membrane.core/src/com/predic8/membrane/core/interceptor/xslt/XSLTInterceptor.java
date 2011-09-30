/* Copyright 2011 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.*;

public class XSLTInterceptor extends AbstractInterceptor {

	private String xslt;
	private XSLTTransformer xsltTransformer = new XSLTTransformer();

	public XSLTInterceptor() {
		name = "XSLT Transformer";
	}

	@Override
	public Outcome handleRequest(Exchange exc) throws Exception {
		transformMsg(exc.getRequest(), xslt);
		return Outcome.CONTINUE;
	}

	@Override
	public Outcome handleResponse(Exchange exc) throws Exception {
		transformMsg(exc.getResponse(), xslt);
		return Outcome.CONTINUE;
	}

	private void transformMsg(Message msg, String ss) throws Exception {
		if (msg.isBodyEmpty())
			return;
		msg.setBodyContent(xsltTransformer.transform(ss,
				new StreamSource(msg.getBodyAsStream()),
				router.getResourceResolver()).getBytes("UTF-8"));
	}

	public String getXslt() {
		return xslt;
	}

	public void setXslt(String xslt) {
		this.xslt = xslt;
	}

	@Override
	protected void writeInterceptor(XMLStreamWriter out)
			throws XMLStreamException {

		out.writeStartElement("transform");

		out.writeAttribute("xslt", xslt);

		out.writeEndElement();
	}

	@Override
	protected void parseAttributes(XMLStreamReader token) {

		xslt = token.getAttributeValue("", "xslt");
	}

}
