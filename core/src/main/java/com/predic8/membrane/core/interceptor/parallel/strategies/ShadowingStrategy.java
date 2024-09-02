package com.predic8.membrane.core.interceptor.parallel.strategies;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.parallel.ParallelStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.parallel.ParallelInterceptor.PARALLEL_TARGET_ID;

public class ShadowingStrategy extends ParallelStrategy {

    private final String targetId;
    private final boolean logShadowResponse;

    private final Logger log = LoggerFactory.getLogger(ShadowingStrategy.class);

    ShadowingStrategy(String targetId, boolean logShadowResponse) throws RuntimeException {
        this.targetId = targetId;
        this.logShadowResponse = logShadowResponse;
    }

    @Override
    public void completeExchange(Exchange exc) {
        super.completeExchange(exc);
        if (exc.getProperty(PARALLEL_TARGET_ID, String.class).equals(targetId)) {
            collectedExchange = exc;
        } else if (logShadowResponse) {
            log.info("Response from %s: %s".formatted(exc.getDestinations().get(0), exc.getResponse()));
        }
    }

    @MCElement(name = "shadowing", topLevel = false)
    public static class ShadowingStrategyElement implements ParallelStrategyElement {

        private String returnTarget;

        private boolean logShadowResponse = false;

        @Override
        public ParallelStrategy getNewInstance() {
            return new ShadowingStrategy(returnTarget, logShadowResponse);
        }

        @Required
        @MCAttribute
        public void setReturnTarget(String returnTarget) {
            this.returnTarget = returnTarget;
        }

        @MCAttribute
        public void setLogShadowResponse(boolean logShadowResponse) {
            this.logShadowResponse = logShadowResponse;
        }
    }
}
