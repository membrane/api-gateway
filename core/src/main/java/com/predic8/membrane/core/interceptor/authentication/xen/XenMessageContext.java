package com.predic8.membrane.core.interceptor.authentication.xen;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class XenMessageContext {

    private static DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    private Exchange exchange;
    private Document doc;
    private Interceptor.Flow flow;
    private static String KEY = "xenMessageContext";

    public static XenMessageContext get(Exchange exchange, Interceptor.Flow flow) {
        XenMessageContext xmc = (XenMessageContext) exchange.getProperty(KEY + flow.toString());
        if (xmc != null)
            return xmc;

        Document document;

        synchronized (dbf) {
            try {
                document = dbf.newDocumentBuilder().parse(getMessage(exchange, flow).getBodyAsStream());
            } catch (SAXException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ParserConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        xmc = new XenMessageContext(exchange, document, flow);
        exchange.setProperty(KEY, xmc);
        return xmc;
    }

    private static Message getMessage(Exchange exchange, Interceptor.Flow flow) {
        switch (flow) {
            case RESPONSE:
                return exchange.getResponse();
            case REQUEST:
                return exchange.getRequest();
            default:
                throw new RuntimeException("not implemented");
        }
    }

    private XenMessageContext(Exchange exchange, Document doc, Interceptor.Flow flow) {
        this.exchange = exchange;
        this.doc = doc;
        this.flow = flow;
    }

    public Document getDocument() {
        return doc;
    }

    public void writeBack() {
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(baos));
            getMessage(exchange, flow).setBodyContent(baos.toByteArray());
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    public void setX(XPath xp, String xPath, String value) throws XPathExpressionException {
        XPathExpression xp3 = xp.compile(xPath);
        NodeList ns = (NodeList) xp3.evaluate(getDocument(), XPathConstants.NODESET);

        if (ns.getLength() != 1)
            throw new RuntimeException("XPath returned nodeset with length != 1.");

        ns.item(0).setTextContent(value);
    }

}
