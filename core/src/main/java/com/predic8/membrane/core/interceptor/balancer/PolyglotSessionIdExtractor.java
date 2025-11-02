/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.balancer;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.lang.Polyglot;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.expression;

@MCElement(name = "sessionIdExtractor")
public class PolyglotSessionIdExtractor extends AbstractXmlElement implements SessionIdExtractor, Polyglot {

    private Language language;
    private String sessionSource;
    private ExchangeExpression exchangeExpression;

    public void init(Router router) {
        if (sessionSource != null && !sessionSource.isEmpty()) {
<<<<<<< HEAD
            exchangeExpression = ExchangeExpression.newInstance(new InterceptorAdapter(router), language, sessionSource);
=======
            exchangeExpression = expression(router, language, sessionSource);
>>>>>>> f78bb2c5a6937831602bd693024f03f9f2acad2d
        }
    }

    public String getSessionId(Exchange exc, Flow flow) throws Exception {
        return exchangeExpression.evaluate(exc, flow, String.class);
    }

    public Language getLanguage() {
        return language;
    }

    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getSessionSource() {
        return sessionSource;
    }

    @Required
    @MCAttribute
    public void setSessionSource(String sessionSource) {
        this.sessionSource = sessionSource;
    }

    public ExchangeExpression getExchangeExpression() {
        return exchangeExpression;
    }

    public void setExchangeExpression(ExchangeExpression exchangeExpression) {
        this.exchangeExpression = exchangeExpression;
    }
}