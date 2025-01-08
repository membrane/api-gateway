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

package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.core.convert.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.*;
import org.springframework.expression.spel.standard.*;

import static org.springframework.expression.spel.SpelCompilerMode.*;

public class SpELExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(SpELExchangeExpression.class);

    private final Expression spelExpression;

    public SpELExchangeExpression(String expression) {
        super(expression);
        spelExpression = new SpelExpressionParser(getSpelParserConfiguration()).parseExpression(expression);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        try {
            return type.cast(spelExpression.getValue(new SpELExchangeEvaluationContext(exchange, exchange.getMessage(flow)), type));
        } catch (SpelEvaluationException see) {
            log.error(see.getLocalizedMessage());
            ExchangeExpressionException eee = new ExchangeExpressionException(expression, see);
            if (see.getCause() instanceof ConverterNotFoundException cnfe) {
                eee.extension("sourceType", cnfe.getSourceType())
                        .extension("targetType", cnfe.getTargetType());
            }
            eee.stacktrace(false);
            throw eee.message(see.getLocalizedMessage());
        }
    }

    private @NotNull SpelParserConfiguration getSpelParserConfiguration() {
        return new SpelParserConfiguration(MIXED, this.getClass().getClassLoader());
    }
}
