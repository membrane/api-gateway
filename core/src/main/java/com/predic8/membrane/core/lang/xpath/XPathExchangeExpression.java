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
import com.predic8.membrane.core.lang.*;
import org.slf4j.*;

import javax.xml.xpath.*;

import static com.predic8.membrane.core.util.XMLUtil.*;
import static javax.xml.xpath.XPathConstants.*;

public class XPathExchangeExpression implements ExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(XPathExchangeExpression.class.getName());

    private static final XPathFactory factory = XPathFactory.newInstance();

    private final String xpath;

    public XPathExchangeExpression(String xpath) {
        this.xpath = xpath;
    }

    @Override
    public boolean evaluate(Exchange exchange, Interceptor.Flow flow) {
        Message msg = exchange.getMessage(flow);
        try {
            // XPath is not thread safe! Therefore every time the factory is called!
            return (Boolean) factory.newXPath().evaluate(xpath, getInputSource(msg), BOOLEAN);
        } catch (XPathExpressionException xe) {
            log.error("Error evaluating XPath {} : {}", xpath, xe.getMessage());
            throw new RuntimeException("Error evaluating XPath " + xpath, xe);
        }
    }
}