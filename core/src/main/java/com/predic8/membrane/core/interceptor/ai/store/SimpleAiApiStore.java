package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@MCElement(name="simpleStore",component = false, id="simple-ai-api-store")
public class SimpleAiApiStore implements AiApiStore {

    private static final Logger log = LoggerFactory.getLogger(SimpleAiApiStore.class);

    private List<AiApiUser> users;
    private AiApiLimit limit = new AiApiLimit();

    @Override
    public void store(String user, Usage usage) {
        log.info("User: {} Usage: {}", user, usage);
        limit.addTokens(usage.totalTokens());
    }

    @Override
    public Optional<AiApiUser> getUser(String token) {
        return users.stream().filter(u -> u.getToken().equals(token)).findFirst();
    }

    @Override
    public long checkLimit(AiApiUser user) {
        return limit.checkLimit();
    }

    @MCChildElement(allowForeign = true,order = 10)
    public void setUsers(List<AiApiUser> users) {
        this.users = users;
    }

    public List<AiApiUser> getUsers() {
        return users;
    }

    public AiApiLimit getLimit() {
        return limit;
    }

    @MCChildElement(allowForeign = true)
    public void setLimit(AiApiLimit limit) {
        this.limit = limit;
    }
}

