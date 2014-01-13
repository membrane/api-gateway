package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import groovy.xml.StreamingMarkupBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerFactory;
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