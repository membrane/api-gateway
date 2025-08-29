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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.AbstractXmlElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.interceptor.lang.Polyglot;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;

@MCElement(name = "sessionIdExtractor")
public class PolyglotSessionIdExtractor extends AbstractXmlElement implements SessionIdExtractor, Polyglot {

    private Language language;
    private String test;
    private ExchangeExpression exchangeExpression;

    public void init(Router router) {
        if (test != null && !test.isEmpty()) {
            exchangeExpression = ExchangeExpression.newInstance(router, language, test);
        }
    }

    public String getSessionId(Exchange exc, Flow flow) throws Exception {
        return exchangeExpression.evaluate(exc, flow, String.class);
    }

    public Language getLanguage() {
        return language;
    }

    @Override
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public ExchangeExpression getExchangeExpression() {
        return exchangeExpression;
    }

    public void setExchangeExpression(ExchangeExpression exchangeExpression) {
        this.exchangeExpression = exchangeExpression;
    }
}