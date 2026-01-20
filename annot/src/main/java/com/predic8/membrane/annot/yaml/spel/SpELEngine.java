package com.predic8.membrane.annot.yaml.spel;

import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static com.predic8.membrane.annot.yaml.spel.HashTemplateParserContext.DOLLAR_TEMPLATE_PARSER_CONTEXT;

public final class SpELEngine {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private SpELEngine() {}

    public static Object evalTemplate(String template, StandardEvaluationContext ctx) {
        try {
            return PARSER
                    .parseExpression(template, DOLLAR_TEMPLATE_PARSER_CONTEXT)
                    .getValue(ctx);
        } catch (RuntimeException e) {
            throw new EvaluationException("Invalid SpEL template: " + e.getMessage(), e);
        }
    }
}
