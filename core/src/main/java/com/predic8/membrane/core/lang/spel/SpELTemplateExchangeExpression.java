package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import org.slf4j.*;

public class SpELTemplateExchangeExpression  extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(TemplateExchangeExpression.class);

    public SpELTemplateExchangeExpression(Router router, String expression) {
        super(expression);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        return null;
    }
}
