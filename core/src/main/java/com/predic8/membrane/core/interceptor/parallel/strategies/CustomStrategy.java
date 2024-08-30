package com.predic8.membrane.core.interceptor.parallel.strategies;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.parallel.CollectionStrategy;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

public class CustomStrategy extends CollectionStrategy {

    private final Expression expr;

    CustomStrategy(Expression expr) throws RuntimeException {
        this.expr = expr;
    }

    @Override
    public void completeExchange(Exchange exc) {
        super.completeExchange(exc);
        collectedExchange = expr.getValue(new CustomStrategyEvaluationContext(runningExchanges, completedExchanges, exc), Exchange.class);
    }

    public static class CustomStrategyEvaluationContext extends StandardEvaluationContext {

        private final List<Exchange> runningExchanges;
        private final List<Exchange> completedExchanges;
        private final Exchange currentExchange;

        public CustomStrategyEvaluationContext(List<Exchange> runningExchanges, List<Exchange> completedExchanges, Exchange currentExchange) {
            super();
            this.runningExchanges = runningExchanges;
            this.completedExchanges = completedExchanges;
            this.currentExchange = currentExchange;
            setRootObject(this);
        }

        public List<Exchange> getRunningExchanges() {
            return runningExchanges;
        }

        public List<Exchange> getCompletedExchanges() {
            return completedExchanges;
        }

        public Exchange getCurrentExchange() {
            return currentExchange;
        }
    }

    @MCElement(name = "custom", topLevel = false, mixed = true)
    public static class CustomStrategyElement implements CollectionStrategyElement {

        private final SpelParserConfiguration spelConfig = new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, this.getClass().getClassLoader());
        private Expression expression;

        @Override
        public CollectionStrategy getNewInstance() {
            return new CustomStrategy(expression);
        }

        @Required
        @MCTextContent
        public void setExpression(String expressionText) {
            this.expression = new SpelExpressionParser(spelConfig).parseExpression(expressionText);
        }
    }
}
