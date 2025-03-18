/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2TokenResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class OAuth2AnswerParameters {

    private final static ObjectMapper om = createObjectMapper();

    private String accessToken;
    private String tokenType;
    private String idToken;
    private Map<String, ?> userinfo = new HashMap<>();
    private String expiration;
    private LocalDateTime receivedAt;
    private String refreshToken;

    public static OAuth2AnswerParameters createFrom(OAuth2TokenResponseBody tokenResponse) {
        var r = new OAuth2AnswerParameters();
        r.readFrom(tokenResponse);
        return r;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public Map<String, ?> getUserinfo() {
        return userinfo;
    }

    public void setUserinfo(Map<String, ?> userinfo) {
        this.userinfo = userinfo;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String serialize() throws JsonProcessingException {
        return OAuth2Util.urlencode(om.writeValueAsString(this));
    }

    public static OAuth2AnswerParameters deserialize(String oauth2answer) throws IOException {
        return om.readValue(OAuth2Util.urldecode(oauth2answer),OAuth2AnswerParameters.class);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
        return mapper;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public String toString() {
        try {
            return om.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public void updateReceivedAt() {
        setReceivedAt(computeReceivedAt());
    }

    private static @NotNull LocalDateTime computeReceivedAt() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime receivedAt = now.withSecond(floorTo30ies(now)).withNano(0);
        return receivedAt;
    }

    private static int floorTo30ies(LocalDateTime now) {
        return now.getSecond() / 30 * 30;
    }

    public void readFrom(OAuth2TokenResponseBody tokenResponse) {
        updateReceivedAt();

        setAccessToken(tokenResponse.getAccessToken());
        setTokenType(tokenResponse.getTokenType());
        setRefreshToken(tokenResponse.getRefreshToken());
        // TODO: "refresh_token_expires_in":1209600
        setExpiration(tokenResponse.getExpiresIn());
        if (tokenResponse.getIdToken() != null)
            setIdToken(tokenResponse.getVerifiedIdToken());
    }
}
