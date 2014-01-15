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
package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;

public class WsaEndpointRewriterInterceptor extends AbstractInterceptor {
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        Writer writer = new StringWriter();

        new WsaEndpointRewriter(getRegistry()).rewriteEndpoint(new InputStreamReader(exc.getRequest().getBodyAsStream()), writer, 2020, exc);
        System.out.println(writer.toString());
        System.out.println(getRegistry().toString());

        exc.getRequest().setBodyContent(writer.toString().getBytes());

        return Outcome.CONTINUE;
    }

    private DecoupledEndpointRegistry getRegistry() {
        return getRouter().getBeanFactory().getBean(DecoupledEndpointRegistry.class);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        System.out.println("WsaEndpointRewriter.handleResponse()");
        return Outcome.CONTINUE;
    }

    private String createWsaDocument() throws ParserConfigurationException {
        return writeDocumentToString(createDom(createBuilder()));
    }

    private String writeDocumentToString(Document document) {
        // TODO createTransformer();
        return null;
    }

    private Document createDom(DocumentBuilder builder) {
        Document doc = builder.newDocument();
        Element envelope = doc.createElementNS("http://www.w3.org/2003/05/soap-envelope", "soap:envelope");
        doc.appendChild(envelope);
        Element soapHeader = doc.createElement("soap:Header");
        envelope.appendChild(soapHeader);
        Element relatesTo = doc.createElement("wsa:RelatesTo");
        soapHeader.appendChild(relatesTo);

        return doc;
    }

    private DocumentBuilder createBuilder() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }
}