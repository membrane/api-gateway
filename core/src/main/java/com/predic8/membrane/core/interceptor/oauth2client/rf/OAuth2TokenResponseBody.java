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
