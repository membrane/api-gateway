package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static java.time.Instant.now;

@MCElement(name = "limit", component = false, id = "ai-api-limit")
public class AiApiLimit {

    private static final Logger log = LoggerFactory.getLogger(AiApiLimit.class);

    private int maxTokens;
    private int period;
    private Instant nextReset;
    private long tokens;

    public AiApiLimit() {
        nextReset = now().plusSeconds(period);
    }

    public long checkLimit() {
        if (now().isAfter(nextReset)) {
            tokens = 0;
            nextReset = now().plusSeconds(period);
            log.debug("Resetting AI API usage limit.");
        }
        return maxTokens - tokens;
    }

    public void addTokens(long tokens) {
        log.debug("Adding {} tokens to AI API usage limit.", tokens);
        this.tokens += tokens;
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
    }
}
