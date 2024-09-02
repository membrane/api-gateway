package com.predic8.membrane.core.interceptor.parallel.strategies;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.parallel.ParallelStrategy;
import com.predic8.membrane.core.lang.groovy.GroovyLanguageSupport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CustomStrategy extends ParallelStrategy {

    private HashMap<String, Object> variableStorage = new HashMap<>();
    private Function<Map<String, Object>, Object> condition;
    private Router router;

    CustomStrategy(Function<Map<String, Object>, Object> condition, Router router) throws RuntimeException {
        this.condition = condition;
        this.router = router;
    }

    @Override
    public void completeExchange(Exchange exc) {
        super.completeExchange(exc);
        collectedExchange = (Exchange) condition.apply(getParameters(runningExchanges, completedExchanges, exc, variableStorage));
    }

    private HashMap<String, Object> getParameters(List<Exchange> runningExchanges,
                    List<Exchange> completedExchanges, Exchange currentExchange, Map<String, Object> variableStorage) {
        return new HashMap<>() {{
            put("spring", router.getBeanFactory());
            put("current", currentExchange);
            put("running", runningExchanges);
            put("completed", completedExchanges);
            put("vars", variableStorage);
        }};
    }

    @MCElement(name = "custom", topLevel = false, mixed = true)
    public static class CustomStrategyElement implements InitializableParallelStrategyElement {

        private String test;
        private Router router;

        @Override
        public void init(Router router) {
            this.router = router;
        }

        @Override
        public ParallelStrategy getNewInstance() {
            return new CustomStrategy(new GroovyLanguageSupport().compileScript(router.getBackgroundInitializator(), null, test), router);
        }

        @Required
        @MCTextContent
        public void setTest(String test) {
            this.test = test;
        }

    }
}
