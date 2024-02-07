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


import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class Challenge {
    public static final String TYPE_HTTP_01 = "http-01";
    public static final String TYPE_DNS_01 = "dns-01";
    public static final String TYPE_TLS_ALPN_01 = "tls-alpn-01";

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
