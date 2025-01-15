package com.predic8.membrane.core.interceptor.flow.choice;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.lang.ExchangeExpression;

import java.util.List;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;

@MCElement(name = "case", topLevel = false)
public class Case extends InterceptorContainer {

    private String test;
    private ExchangeExpression.Language language = SPEL;
    private ExchangeExpression exchangeExpression;

    public void init(Router router) {
        exchangeExpression = ExchangeExpression.getInstance(router, language, test);
    }

    public ExchangeExpression getExchangeExpression() {
        return exchangeExpression;
    }

    public ExchangeExpression.Language getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @default groovy
     * @example SpEL, groovy, jsonpath, xpath
     */
    @MCAttribute
    public void setLanguage(ExchangeExpression.Language language) {
        this.language = language;
    }

    public String getTest() {
        return test;
    }

    /**
     * @description the condition to be tested
     * @example exc.request.header.userAgentSupportsSNI
     */
    @Required
    @MCAttribute
    public void setTest(String test) {
        this.test = test;
    }
}