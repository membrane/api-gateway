/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot.yaml.spel;

import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static com.predic8.membrane.annot.yaml.spel.HashTemplateParserContext.DOLLAR_TEMPLATE_PARSER_CONTEXT;

public final class SpELEngine {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private SpELEngine() {}

    public static Object eval(String template, StandardEvaluationContext ctx) {
        try {
            return PARSER
                    .parseExpression(template, DOLLAR_TEMPLATE_PARSER_CONTEXT)
                    .getValue(ctx);
        } catch (RuntimeException e) {
            throw new EvaluationException("Invalid SpEL template: " + e.getMessage(), e);
        }
    }
}
