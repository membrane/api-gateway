package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.joda.time.DateTime;

import java.util.List;

public class Authorization {
    public static final String AUTHORIZATION_STATUS_PENDING = "pending";
    public static final String AUTHORIZATION_STATUS_VALID = "valid";
    public static final String AUTHORIZATION_STATUS_INVALID = "invalid";
    public static final String AUTHORIZATION_STATUS_REVOKED = "revoked";
    public static final String AUTHORIZATION_STATUS_DEACTIVATED = "deactivated";
    public static final String AUTHORIZATION_STATUS_EXPIRED = "expired";

    Identifier identifier;
    String status;
    DateTime expires;
    List<Challenge> challenges;
    boolean wildcard;

    public Identifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(Identifier identifier) {
        this.identifier = identifier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public DateTime getExpires() {
        return expires;
    }

    public void setExpires(DateTime expires) {
        this.expires = expires;
    }

    public List<Challenge> getChallenges() {
        return challenges;
    }

    public void setChallenges(List<Challenge> challenges) {
        this.challenges = challenges;
    }

    public boolean isWildcard() {
        return wildcard;
    }

    public void setWildcard(boolean wildcard) {
        this.wildcard = wildcard;
    }
}
