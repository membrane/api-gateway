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
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.spel.spelable.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;
import org.springframework.core.convert.*;
import org.springframework.expression.*;
import org.springframework.expression.common.*;
import org.springframework.expression.spel.*;
import org.springframework.expression.spel.standard.*;

import static org.springframework.expression.spel.SpelCompilerMode.*;

public class SpELExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(SpELExchangeExpression.class);

    private final Expression spelExpression;

    /**
     * Creates an expression "expr" or a templated "..${expr}...${expr}...", depending on
     * the second parameter.
     *
     * @param expression SpEL expression
     * @param parserContext null or one with configuration of prefix and suffix e.g. ${ and }
     */
    public SpELExchangeExpression(String expression, TemplateParserContext parserContext) {
        super(expression);
        String errorMessage;
        String posLine = "";
        try {
            spelExpression = new SpelExpressionParser(getSpelParserConfiguration()).parseExpression( expression, parserContext );
            return;
        } catch (ParseException e) {
            var pos = e.getPosition();
            posLine = " ".repeat(pos) + "^";
            errorMessage = e.getLocalizedMessage();
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
        }
        throw new ConfigurationException("""
                    The expression:
                    
                    %s
                    %s
                    
                    caused:
                    
                    %s
                    """.formatted(expression, posLine, errorMessage));
    }

    @Override
    public <T> T evaluate(Exchange exchange, Interceptor.Flow flow, Class<T> type) {
        try {
            Object o = spelExpression.getValue(new SpELExchangeEvaluationContext(exchange, exchange.getMessage(flow)),Object.class);
            if (o == null) {
                return null;
            }
            if (o instanceof SpELLablePropertyAware spa) {
                return type.cast( spa.getValue());
            }
            if (type.getName().equals("java.lang.String")) {
                return type.cast(o.toString());
            }
            if (type.getName().equals("java.lang.Object")) {
                if (o instanceof String s) {
                    if (s.isEmpty())
                        return null;
                }
            }
            if (type.isInstance(o))
                return type.cast(o);
            throw new RuntimeException("Cannot cast!");
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

    public static class DollarBracketTemplateParserContext extends TemplateParserContext {
        public DollarBracketTemplateParserContext() {
            super("${","}");
        }
    }
}
