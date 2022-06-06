package com.predic8.membrane.core.transport.ssl.acme;


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class Challenge {
    public static final String TYPE_HTTP_01 = "http-01";

    public static final String CHALLENGE_STATUS_PENDING = "pending";
    public static final String CHALLENGE_STATUS_VALID = "valid";
    public static final String CHALLENGE_STATUS_PROCESSING = "processing";
    public static final String CHALLENGE_STATUS_INVALID = "invalid";

    String type;
    String status;
    String url;
    String token;
    Map<String,Object> other = new HashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @JsonAnySetter
    public void setOther(String name, Object value) {
        this.other.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getOther() {
        return other;
    }

}
