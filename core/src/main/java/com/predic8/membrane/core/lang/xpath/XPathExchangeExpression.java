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
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.XMLSupport;
import com.predic8.membrane.core.lang.AbstractExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.XPathUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import com.predic8.membrane.core.util.xml.parser.XmlParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpressionException;

import static com.predic8.membrane.core.util.text.StringUtil.tail;
import static com.predic8.membrane.core.util.text.StringUtil.truncateAfter;
import static javax.xml.xpath.XPathConstants.NODESET;

public class XPathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(XPathExchangeExpression.class.getName());

    private static final XmlParser parser = HardenedXmlParser.getInstance();

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

        // XPath is not thread safe!
        var xPath = XPathUtil.newXPath(xmlConfig);

        try {
            if (xmlType == null) {
                return xPath.evaluateExpression(expression, parser.parse(XMLUtil.getInputSource(msg)));
            }
            try {
                // Depending on the xpath it is not always possible to set it to specified xmlType
                // e.g., xmlType=NodeSet xpath=string(//city)
                return xPath.evaluate(expression, parser.parse(XMLUtil.getInputSource(msg)), xmlType);
            } catch (XPathExpressionException e) {
                log.debug("XPath expression failed. Trying again without type.", e);
                return xPath.evaluateExpression(expression, parser.parse(XMLUtil.getInputSource(msg)));
            }
        } catch (RuntimeException e) {
            // Parser errors may escape as unchecked exceptions.
            // Matches: prolog and Prolog
            if (causeMessageContains(e, "rolog")) {
                throw new ExchangeExpressionException(expression, e, "Content not allowed in prolog of XML input.")
                        .detail("There are extra characters before the XML declaration <?xml ... ?>")
                        .body(truncateAfter(msg.getBodyAsStringDecoded(), 50))
                        .excludeException();
            }

            // Matches: Content and content
            if (causeMessageContains(e, "ontent")) {
                throw new ExchangeExpressionException(expression, e, "Content not allowed in trailing section of XML input.")
                        .detail("There are extra characters after the XML root element (after the final closing tag like </root>).")
                        .body(tail(msg.getBodyAsStringDecoded(), 50))
                        .excludeException();
            }
            throw e;
        }
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
