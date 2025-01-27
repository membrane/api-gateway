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
import com.predic8.membrane.core.interceptor.Interceptor.*;
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

import static java.lang.Boolean.*;
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
        Exception exception;
        String posLine = "";
        try {
            spelExpression = new SpelExpressionParser(getSpelParserConfiguration()).parseExpression( expression, parserContext );
            return;
        } catch (ParseException e) {
            var pos = e.getPosition();
            posLine = " ".repeat(pos) + "^";
            exception = e;
        }
        catch (Exception e) {
            exception = e;
        }
        throw new ConfigurationException("""
                    Error in expression:
                    
                    %s
                    %s
                    """.formatted(expression, posLine), exception);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Flow flow, Class<T> type) {
        try {
            Object o = evaluate(exchange, flow);
            if (Boolean.class.isAssignableFrom(type)) {
                return type.cast(toBoolean(o));
            }
            if (String.class.isAssignableFrom(type)) {
                return type.cast(toString(o));
            }
            if (Object.class.isAssignableFrom(type)) {
                return type.cast( toObject(o));
            }
            throw new RuntimeException("Cannot cast {} to {}".formatted(o,type));
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

    private @Nullable Object evaluate(Exchange exchange, Flow flow) {
        return spelExpression.getValue(new SpELExchangeEvaluationContext(exchange, flow), Object.class);
    }

    private static Object toObject(Object o) {
        if (o == null) {
            return null;
        }
        switch (o) {
            case String s: {
                if (s.isEmpty())
                    return null;
                break;
            }
            case SpELLablePropertyAware spa:
                return spa.getValue();
            default:
        }
        return o;
    }

    private static String toString(Object o) {
        if (o == null)
            return "";
        if (o instanceof SpELLablePropertyAware spa) {
            return spa.getValue().toString();
        }
        return o.toString();
    }

    private static boolean toBoolean(Object o) {
        if (o == null) {
            return FALSE;
        }
        switch (o) {
            case String s: {
                if (s.isEmpty())
                    return FALSE;
                return Boolean.parseBoolean(s);
            }
            case SpELLablePropertyAware spa:
                return spa.getValue() != null;
            default:
        }
        if (o instanceof Boolean b) {
            return b;
        }
        return FALSE;
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
