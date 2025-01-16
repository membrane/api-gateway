package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.lang.*;

public class AbstractExchangeExpressionInterceptor extends AbstractLanguageInterceptor {

    protected ExchangeExpression exchangeExpression;
    protected String expression = ""; // default if there is no expression

    @Override
    public void init() {
        super.init();
        exchangeExpression = TemplateExchangeExpression.newInstance(router, language, expression);
    }

}
