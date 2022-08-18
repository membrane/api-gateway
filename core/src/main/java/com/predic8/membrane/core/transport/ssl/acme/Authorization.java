/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
