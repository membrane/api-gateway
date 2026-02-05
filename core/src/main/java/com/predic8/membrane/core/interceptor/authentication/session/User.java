package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.*;

public class User {

    final Map<String, String> attributes = new HashMap<>();

    public User(String username, String password) {
        setUsername(username);
        setPassword(password);
    }

    public User() {
    }

    public String getUsername() {
        return attributes.get("username");
    }

    public void setUsername(String value) {
        attributes.put("username", value);
    }

    public String getPassword() {
        return attributes.get("password");
    }

    public void setPassword(String value) {
        attributes.put("password", value);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes.putAll(attributes);
    }
}
