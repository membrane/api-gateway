/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang.xpath;

import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.XmlDomBody;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.XMLSupport;
import com.predic8.membrane.core.lang.AbstractExchangeExpression;
import com.predic8.membrane.core.lang.BodyKind;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.xml.XPathUtil;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpressionException;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.text.StringUtil.tail;
import static com.predic8.membrane.core.util.text.StringUtil.truncateAfter;
import static javax.xml.xpath.XPathConstants.NODESET;

public class XPathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(XPathExchangeExpression.class.getName());

    /**
     * DocumentBuilder is not thread-safe. Only builds empty documents, so it parses nothing that would need hardening.
     */
    private static final ThreadLocal<DocumentBuilder> DOCUMENT_BUILDER = ThreadLocal.withInitial(XPathExchangeExpression::newDocumentBuilder);

    private XmlConfig xmlConfig;

    public XPathExchangeExpression(Interceptor interceptor, String xpath, Router router) {
        super(xpath, router);

        if (interceptor instanceof XMLSupport xns) {
            xmlConfig = xns.getXmlConfig();
        }
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        var msg = exchange.getMessage(flow);
        try {
            if (Boolean.class.isAssignableFrom(type)) {
                return type.cast(evaluateAndCast(msg, XPathConstants.BOOLEAN));
            }
            if (String.class.isAssignableFrom(type)) {
                return type.cast(evaluateAndCast(msg, XPathConstants.STRING));
            }
            if (Object.class.isAssignableFrom(type)) {
                return type.cast(evaluateAndCastToObject(msg));
            }
            throw new RuntimeException("Should not Happen!");
        } catch (XPathExpressionException e) {
            throw getExchangeExpressionException(e).body(msg.getBodyAsStringDecoded());
        }
    }

    private @NotNull ExchangeExpressionException getExchangeExpressionException(XPathExpressionException e) {
        var eee = new ExchangeExpressionException(expression, e);
        if (e.getMessage() != null && e.getMessage().contains("Prefix must resolve to a namespace")) {
            var m = "XML prefix is not mapped to a namespace.";
            if (xmlConfig != null && xmlConfig.getNamespaces() != null) {
                m += " Check prefix with xmlConfig.";
            } else {
                m += " xmlConfig and namespace declaration is missing.";
            }
            eee.detail(m);
        }
        return eee;
    }

    private Object evaluateAndCastToObject(Message msg) throws XPathExpressionException {
        var t = evaluateAndCast(msg, NODESET);
        if (t instanceof XPathEvaluationResult<?> xpr) {
            return xpr.value();
        }
        if (t instanceof NodeList nl) {
            return nl;
        }
        log.debug("That point should not be reached.");
        return t;
    }

    private Object evaluateAndCast(Message msg, QName xmlType) throws XPathExpressionException {
        if (log.isDebugEnabled()) {
            log.debug("Evaluating: {}", expression);
            log.debug("Body: {}", msg.getBodyAsStringDecoded()); // is expensive!
        }

        return switch (BodyKind.of(msg, XPathExchangeExpression::isXml)) {
            case EMPTY, FOREIGN -> evaluateOnEmptyDocument(xmlType);
            case MATCHING, UNKNOWN -> evaluateOnBody(msg, xmlType);
            case FORM -> evaluateOnFormBody(msg, xmlType);
        };
    }

    /**
     * XOP/MTOM packages are multipart/related; {@link Message#getBodyAsStreamDecoded()} reassembles them into XML.
     */
    private static boolean isXml(String contentType) {
        return isXML(contentType) || isOfMediaType(MULTIPART_RELATED, contentType);
    }

    private Object evaluateOnBody(Message msg, QName xmlType) throws XPathExpressionException {
        try {
            return evaluate(msg, xmlType);
        } catch (XmlParseException e) {
            throw bodyError(msg, e);
        }
    }

    /**
     * Form data is what <code>curl -d</code> sends for any body, so it may be mislabelled XML. Anything
     * else is the form data it claims to be, which holds no XML document.
     */
    private Object evaluateOnFormBody(Message msg, QName xmlType) throws XPathExpressionException {
        try {
            return evaluate(msg, xmlType);
        } catch (XmlParseException e) {
            log.info("Content-Type is {} and the body is not XML, so XPath {} is evaluated against an empty document. To have the body evaluated, send Content-Type: {}",
                    APPLICATION_X_WWW_FORM_URLENCODED, expression, APPLICATION_XML);
            return evaluateOnEmptyDocument(xmlType);
        }
    }

    private Object evaluate(Message msg, QName xmlType) throws XPathExpressionException {
        var namespaces = namespaceContext();
        if (xmlType == null) {
            return XmlDomBody.xpath(msg, expression, namespaces);
        }
        try {
            // Depending on the xpath it is not always possible to set it to specified xmlType
            // e.g., xmlType=NodeSet xpath=string(//city)
            return XmlDomBody.xpath(msg, expression, namespaces, xmlType);
        } catch (XPathExpressionException e) {
            log.debug("XPath expression failed. Trying again without type.", e);
            return XmlDomBody.xpath(msg, expression, namespaces);
        }
    }

    /**
     * A fresh document per call: DOM implementations do not guarantee thread safe reads, not even of
     * an empty document.
     */
    private Object evaluateOnEmptyDocument(QName xmlType) throws XPathExpressionException {
        var emptyDocument = DOCUMENT_BUILDER.get().newDocument();
        var xpath = XPathUtil.newXPath(namespaceContext());
        if (xmlType == null) {
            return xpath.evaluateExpression(expression, emptyDocument);
        }
        try {
            return xpath.evaluate(expression, emptyDocument, xmlType);
        } catch (XPathExpressionException e) {
            log.debug("XPath expression failed. Trying again without type.", e);
            return xpath.evaluateExpression(expression, emptyDocument);
        }
    }

    private ExchangeExpressionException bodyError(Message msg, XmlParseException e) {
        // Matches: prolog and Prolog
        if (causeMessageContains(e, "rolog")) {
            return new ExchangeExpressionException(expression, e, "Content not allowed in prolog of XML input.")
                    .detail("There are extra characters before the XML declaration <?xml ... ?>")
                    .body(truncateAfter(msg.getBodyAsStringDecoded(), 50))
                    .excludeException()
                    .bodyError();
        }

        // Matches: Content and content
        if (causeMessageContains(e, "ontent")) {
            return new ExchangeExpressionException(expression, e, "Content not allowed in trailing section of XML input.")
                    .detail("There are extra characters after the XML root element (after the final closing tag like </root>).")
                    .body(tail(msg.getBodyAsStringDecoded(), 50))
                    .excludeException()
                    .bodyError();
        }
        return new ExchangeExpressionException(expression, e, e.getMessage())
                .body(truncateAfter(msg.getBodyAsStringDecoded(), 50))
                .excludeException()
                .bodyError();
    }

    private static DocumentBuilder newDocumentBuilder() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Cannot create a DocumentBuilder.", e);
        }
    }

    private @Nullable NamespaceContext namespaceContext() {
        return xmlConfig != null && xmlConfig.getNamespaces() != null ? xmlConfig.getNamespaces().getNamespaceContext() : null;
    }

    private static boolean causeMessageContains(Throwable t, String fragment) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (m != null && m.contains(fragment))
                return true;
        }
        return false;
    }

    public void setXmlConfig(XmlConfig xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
