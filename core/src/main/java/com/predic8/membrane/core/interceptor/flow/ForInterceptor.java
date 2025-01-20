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
package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;

@MCElement(name = "for")
public class ForInterceptor extends AbstractFlowInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ForInterceptor.class);

    private String in;
    private Language language = SPEL;

    private ExchangeExpression exchangeExpression;

    @Override
    public void init() {
        super.init();
        try {
            exchangeExpression = ExchangeExpression.newInstance(router, language, in);
        } catch (ConfigurationException ce) {
            throw new ConfigurationException(ce.getMessage() + """
                    
                    <for in="%s">""".formatted(in), ce.getCause());
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        Object o;
        try {
            o = exchangeExpression.evaluate(exc, flow, Object.class);
        } catch (ExchangeExpressionException e) {
           ProblemDetails pd =  ProblemDetails.internal(router.isProduction(), getDisplayName());
            e.provideDetails(pd)
                    .detail("Error evaluating expression on exchange.")
                    .component(getDisplayName())
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        if (o instanceof List<?> l) {
            log.debug("List detected {}",l);
            for (Object o2 : l) {
                log.debug("type: {}, it: {}",o2.getClass(),o2);
                if (flow.isRequest()) {
                    exc.setProperty("it", o2);
                    getFlowController().invokeRequestHandlers(exc, interceptors);
                }
            }
        }

        return CONTINUE;
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @default groovy
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getIn() {
        return in;
    }

    /**
     *
     */
    @Required
    @MCAttribute
    public void setIn(String in) {
        this.in = in;
    }

    @Override
    public String getDisplayName() {
        return "for";
    }

    @Override
    public String getShortDescription() {
        StringBuilder ret = new StringBuilder("for (" + in + ") {");
        for (Interceptor i : getInterceptors()) {
            ret.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;").append(i.getDisplayName());
        }
        ret.append("<br/>}");
        return ret.toString();
    }
}
