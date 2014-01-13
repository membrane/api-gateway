package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.exchange.Exchange;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

public class DecoupledEndpointRewriter {
    private static final String ADDRESSING_URI = "http://www.w3.org/2005/08/addressing";

    private final DocumentBuilderFactory builderFactory;
    private final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private final DecoupledEndpointRegistry registry;

    public DecoupledEndpointRewriter(DecoupledEndpointRegistry registry) {
        builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);

        this.registry = registry;
    }

    public void rewriteToElement(InputStream reader, Writer writer, Exchange exc) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        Document doc = getDocument(reader);
        String uri = getRelatesToValue(doc);
        setTarget(exc, uri);
        setToElement(doc, uri);
        writeDocument(writer, doc);
    }

    private void setTarget(Exchange exc, String uri) {
        exc.getDestinations().set(0, registry.lookup(uri));
    }

    private void writeDocument(Writer writer, Document doc) throws TransformerException {
        transformerFactory.newTransformer().transform(new DOMSource(doc), new StreamResult(writer));
    }

    private Document getDocument(InputStream reader) throws SAXException, IOException, ParserConfigurationException {
        return builderFactory.newDocumentBuilder().parse(reader);
    }

    private void setToElement(Document doc, String relatesTo) {
        doc.getElementsByTagNameNS(ADDRESSING_URI, "To").item(0).setTextContent(registry.lookup(relatesTo));
    }

    private String getRelatesToValue(Document doc) {
        return doc.getElementsByTagNameNS(ADDRESSING_URI, "RelatesTo").item(0).getTextContent();
    }
}