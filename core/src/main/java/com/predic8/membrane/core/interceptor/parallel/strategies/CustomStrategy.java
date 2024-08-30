package com.predic8.membrane.core.interceptor.parallel.strategies;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.parallel.CollectionStrategy;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;

public class CustomStrategy extends CollectionStrategy {

    private final Expression expr;
    private final CustomStrategyEvaluationContext evalCtx = new CustomStrategyEvaluationContext();

    CustomStrategy(Expression expr) throws RuntimeException {
        this.expr = expr;
        evalCtx.setRunningExchanges(runningExchanges);
        evalCtx.setCompletedExchanges(completedExchanges);
    }

    @Override
    public void completeExchange(Exchange exc) {
        super.completeExchange(exc);
        evalCtx.setCurrentExchange(exc);
        collectedExchange = expr.getValue(evalCtx, Exchange.class);
    }

    public static class CustomStrategyEvaluationContext extends StandardEvaluationContext {

        private List<Exchange> runningExchanges;
        private List<Exchange> completedExchanges;
        private Exchange currentExchange;

        public CustomStrategyEvaluationContext() {
            super();
            setRootObject(this);
        }

        public void setRunningExchanges(List<Exchange> runningExchanges) {
            this.runningExchanges = runningExchanges;}
        public void setCompletedExchanges(List<Exchange> completedExchanges) {
            this.completedExchanges = completedExchanges;
        }
        public void setCurrentExchange(Exchange currentExchange) {
            this.currentExchange = currentExchange;}
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
