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
import org.jetbrains.annotations.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.*;
import org.springframework.expression.spel.standard.*;

import static org.springframework.expression.spel.SpelCompilerMode.MIXED;

public class SpELExchangeExpression implements ExchangeExpression {

    private final Expression expression;

    private final String source;

    public SpELExchangeExpression(String source) {
        this.source = source;
        expression = new SpelExpressionParser(getSpelParserConfiguration()).parseExpression(source);
    }

    @Override
    public boolean evaluate(Exchange exchange, Interceptor.Flow flow) {
        return expression.getValue(new ExchangeEvaluationContext(exchange, exchange.getMessage(flow)), Boolean.class);
    }

    private @NotNull SpelParserConfiguration getSpelParserConfiguration() {
        return new SpelParserConfiguration(MIXED, this.getClass().getClassLoader());
    }
}
