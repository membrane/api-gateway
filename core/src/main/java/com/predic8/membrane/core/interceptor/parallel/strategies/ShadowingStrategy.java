package com.predic8.membrane.core.interceptor.parallel.strategies;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.parallel.CollectionStrategy;
import com.predic8.membrane.core.interceptor.parallel.CollectionStrategy.CollectionStrategyElement;

import java.util.List;

public class ShadowingStrategy extends CollectionStrategy {

    @Override
    public void completeExchange(Exchange exc) {
        super.completeExchange(exc);
        if (runningExchanges.isEmpty()) {
            collectedExchange = exc;
        }
    }

    @MCElement(name = "shadowing", topLevel = false)
    public class ShadowingStrategyElement implements CollectionStrategyElement {
        @Override
        public CollectionStrategy getNewInstance() {
            return new ShadowingStrategy();
        }
    }
}
