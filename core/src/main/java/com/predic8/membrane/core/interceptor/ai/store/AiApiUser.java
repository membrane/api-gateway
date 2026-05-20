package com.predic8.membrane.core.interceptor.ai.store;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "users", component = false, id="ai-api-users")
public class AiApiUser {

    private String name;
    private String token;

    public String getName() {
        return name;
    }

    /**
     * @description Name of the API user, group or cost center.
     * @param name of the user
     */
    @MCAttribute()
    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    @MCAttribute()
    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "user(name: %s)".formatted(name);
    }
}
