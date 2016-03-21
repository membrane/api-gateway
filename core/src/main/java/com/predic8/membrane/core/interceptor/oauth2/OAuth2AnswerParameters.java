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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class OAuth2AnswerParameters {

    private String accessToken;
    private String tokenType;
    private String idToken;
    private HashMap<String,String> userinfo = new HashMap<String, String>();

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

    public HashMap<String, String> getUserinfo() {
        return userinfo;
    }

    public void setUserinfo(HashMap<String, String> userinfo) {
        this.userinfo = userinfo;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String serialize() throws JsonProcessingException, UnsupportedEncodingException {
        return OAuth2Util.urlencode(new ObjectMapper().writeValueAsString(this));
    }

    public static OAuth2AnswerParameters deserialize(String oauth2answer) throws IOException {
        return new ObjectMapper().readValue(OAuth2Util.urldecode(oauth2answer),OAuth2AnswerParameters.class);
    }
}
