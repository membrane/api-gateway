/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils.numberToString;

public class OAuth2TokenResponseBody {
    private final Map<String, Object> json;
    private final AuthorizationService auth;

    public OAuth2TokenResponseBody(AuthorizationService auth, InputStream body) throws IOException {
        this.auth = auth;
        try {
            json = new ObjectMapper().readValue(body, new TypeReference<>() {
            });
        } catch (IOException e) {
            // in case the ObjectMapper has not read the HTTP body completely,
            // we need to remove the body from the stream, so that the HTTPClient Connection Manager
            // can close the TCP connection.
            IOUtils.toByteArray(body);
            throw e;
        }
    }

    public static OAuth2TokenResponseBody parse(AuthorizationService auth, InputStream body) throws IOException {
        return new OAuth2TokenResponseBody(auth, body);
    }

    public boolean isMissingOneToken() {
        return json.get("access_token") == null || json.get("refresh_token") == null;
    }

    public String getAccessToken() {
        return (String) json.get("access_token");
    }

    public String getTokenType() {
        return (String) json.get("token_type");
    }

    public String getRefreshToken() {
        return (String) json.get("refresh_token");
    }

    public String getExpiresIn() {
        return numberToString(json.get("expires_in"));
    }

    public String getIdToken() {
        return (String) json.get("id_token");
    }

    public String getVerifiedIdToken() {
        if (auth.idTokenIsValid(getIdToken())) {
            return getIdToken();
        }
        return "INVALID";
    }

    @Override
    public String toString() {
        return "[OAuth2TokenResponseBody] " + json;
    }
}
