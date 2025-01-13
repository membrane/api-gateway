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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;
import org.slf4j.*;

import javax.xml.namespace.*;
import javax.xml.xpath.*;

import static com.predic8.membrane.core.util.XMLUtil.*;
import static javax.xml.xpath.XPathConstants.*;

public class XPathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(XPathExchangeExpression.class.getName());

    private static final XPathFactory factory = XPathFactory.newInstance();

    public XPathExchangeExpression(String xpath) {
        super(xpath);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        Message msg = exchange.getMessage(flow);
        try {
            if (type.isAssignableFrom(Boolean.class)) {
                // XPath is not thread safe! Therefore every time the factory is called!
                return evalutateAndCast(msg, BOOLEAN, type);
            }
            if (type.isAssignableFrom(String.class)) {
                return evalutateAndCast(msg, STRING, type);
            }
            throw  new RuntimeException("Should not Happen!");
        } catch (XPathExpressionException xee) {
            throw new ExchangeExpressionException(expression,xee)
                    .message(xee.getLocalizedMessage())
                    .body(msg.getBodyAsStringDecoded())
                    .stacktrace(false);
        }
    }

    private <T> T evalutateAndCast(Message msg, QName xmlType, Class<T> expectedJavaType) throws XPathExpressionException {
        return expectedJavaType.cast(factory.newXPath().evaluate(expression, getInputSource(msg), xmlType));
    }
}