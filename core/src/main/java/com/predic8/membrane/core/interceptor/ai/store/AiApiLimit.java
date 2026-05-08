package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static java.time.Instant.now;

@MCElement(name = "limit", component = false, id = "ai-api-limit")
public class AiApiLimit {

    private static final Logger log = LoggerFactory.getLogger(AiApiLimit.class);

    private int maxTokens;
    private int period;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private Instant nextReset;

    private final AtomicLong tokens = new AtomicLong(0);

    /**
     * Checks if the user has enough tokens to make the request.
     * If there aren't enough tokens for the request, 0 or a negative number is returned.
     *
     * @param tokensForNextRequest
     * @return Estimated remaining tokens after this call.
     */
    public long checkLimit(long tokensForNextRequest) {
        Instant now = now();

        if (nextReset == null || now.isAfter(nextReset)) {
            synchronized (lock) {
                tokens.set(0);
                nextReset = now.plusSeconds(period);
                log.debug("Resetting AI API usage limit.");
            }
        }

        return maxTokens - tokens.get() - tokensForNextRequest;
    }

    public void addTokens(long tokens) {
        log.debug("Adding {} tokens to AI API usage limit.", tokens);
        this.tokens.addAndGet(tokens);
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    @MCAttribute
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getPeriod() {
        return period;
    }

    @MCAttribute
    public void setPeriod(int period) {
        this.period = period;
        nextReset = now().plusSeconds(period);
    }
}
