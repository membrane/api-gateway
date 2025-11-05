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

import com.predic8.membrane.core.config.xml.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.util.xml.*;
import com.predic8.membrane.core.util.xml.parser.*;
import org.slf4j.*;
import org.w3c.dom.*;

import javax.xml.namespace.*;
import javax.xml.xpath.*;

import static javax.xml.xpath.XPathConstants.*;

public class XPathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(XPathExchangeExpression.class.getName());

    private static final XmlParser parser = HardenedXmlParser.getInstance();

    private XmlConfig xmlConfig;

    // Let all expressions share the same XPathFactory.
    private static final XPathFactory factory = XPathFactory.newInstance();

    public XPathExchangeExpression(Interceptor interceptor, String xpath) {
        super(xpath);

        if (interceptor instanceof XMLSupport xns) {
            xmlConfig = xns.getXmlConfig();
        }
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        Message msg = exchange.getMessage(flow);
        try {
            if (Boolean.class.isAssignableFrom(type)) {
                return type.cast( evalutateAndCast(msg, BOOLEAN));
            }
            if (String.class.isAssignableFrom(type)) {
                return type.cast(evalutateAndCast(msg, STRING));
            }
            if (Object.class.isAssignableFrom(type)) {
                return type.cast( evaluateAndCastToObject( msg));
            }
            throw  new RuntimeException("Should not Happen!");
        } catch (XPathExpressionException xee) {
            throw new ExchangeExpressionException(expression,xee)
                    .body(msg.getBodyAsStringDecoded())
                    .stacktrace(false);
        }
    }

    private Object evaluateAndCastToObject(Message msg) throws XPathExpressionException {
        Object t = evalutateAndCast(msg, NODESET);
        if (t instanceof NodeList nl) {
            return new NodeListWrapper(nl);
        }
        return t;
    }

    private Object evalutateAndCast(Message msg, QName xmlType) throws XPathExpressionException {
        if (log.isDebugEnabled()) {
            log.debug("Evaluating: {}", expression);
            log.debug("Body: {}", msg.getBodyAsStringDecoded()); // is expensive!
        }

        // XPath is not thread safe! Therefore, every time the factory is called!
        XPath xPath = factory.newXPath();

        if (xmlConfig != null && xmlConfig.getNamespaces() != null) {
            xPath.setNamespaceContext(xmlConfig.getNamespaces().getNamespaceContext());
        }

        return xPath.evaluate(expression, parser.parse(XMLUtil.getInputSource(msg)), xmlType);
    }

    public void setXmlConfig(XmlConfig xmlConfig) {
        this.xmlConfig = xmlConfig;
    }
}
