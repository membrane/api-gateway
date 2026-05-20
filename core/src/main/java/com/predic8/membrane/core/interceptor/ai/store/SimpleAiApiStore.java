package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.time.Instant.now;

/**
 * @description Simple store for the LLM Gateway that stores limits in memory. Users and keys can
 * be configured in the configuration file.
 */
@MCElement(name="simpleStore",component = false, id="simple-ai-api-store")
public class SimpleAiApiStore implements AiApiStore {

    private static final Logger log = LoggerFactory.getLogger(SimpleAiApiStore.class);

    @GuardedBy("lock")
    private List<AiApiUser> users = Collections.emptyList();

    private boolean logUsage = true;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private Instant nextReset;

    private long limitResetPeriod = 60;

    @Override
    public void store(AiApiUser user, Usage usage) {
        if (logUsage)
            log.info("user: {} {}",user.getName(),usage.toString());
        user.addTokensUsedInPeriod(usage);
    }

    @Override
    public Optional<AiApiUser> getUser(String token) {
        synchronized (lock) {
            return users.stream().filter(u -> u.getApiKey().equals(token)).findFirst();
        }
    }

    @Override
    public long checkLimit(AiApiUser user, long inputTokens, long outputTokens) {
        if (user == null)
            return 0; // anonymous user gets no tokens

        synchronized (lock) {
            var now = now();
            if (nextReset == null || now.isAfter(nextReset)) {
                nextReset = now.plusSeconds(limitResetPeriod);
                log.info("Resetting AI API token usage limit.");
                users.forEach(AiApiUser::resetTokensUsedInPeriod);
            }
        }

        return user.checkLimit(inputTokens + outputTokens);
    }

    @Override
    public long getRemainingResetTime() {
        synchronized (lock) {
            return nextReset == null ? 0 : (nextReset.toEpochMilli() - now().toEpochMilli()) / 1000;
        }
    }


    /**
     * List of users that can be used for authentication.
     * @param users User list
     */
    @MCChildElement(allowForeign = true,order = 10)
    public void setUsers(List<AiApiUser> users) {
        synchronized (lock) {
            this.users = users;
        }
    }

    public List<AiApiUser> getUsers() {
        return users;
    }

    public long getLimitResetPeriod() {
        return limitResetPeriod;
    }

    /**
     * @description The period in seconds after which the token limit is reset.
     * @param limitResetPeriod in seconds, e.g. 3600 for 1 hour
     */
    @MCAttribute
    public void setLimitResetPeriod(long limitResetPeriod) {
        this.limitResetPeriod = limitResetPeriod;
    }

    public boolean isLogUsage() {
        return logUsage;
    }

    public void setLogUsage(boolean logUsage) {
        this.logUsage = logUsage;
    }
}

