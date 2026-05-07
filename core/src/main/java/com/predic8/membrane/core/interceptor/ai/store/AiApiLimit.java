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

    private AtomicLong tokens = new AtomicLong(0);

    public long checkLimit() {
        Instant now = now();

        if (now.isAfter(nextReset)) {
            synchronized (lock) {
                tokens.set(0);
                nextReset = now.plusSeconds(period);
                log.debug("Resetting AI API usage limit.");
            }
        }

        return maxTokens - tokens.get();
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
