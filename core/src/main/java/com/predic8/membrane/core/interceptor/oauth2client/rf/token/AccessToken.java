package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.session.Session;

import java.io.UnsupportedEncodingException;

public class AccessToken {

    public static final String OAUTH2_ANSWER = "oauth2Answer";

    private final Session session;

    public AccessToken(Session session) {
        this.session = session;
    }

    public void updateAccessToken(String token) {
        session.put(ParamNames.ACCESS_TOKEN, token);
    }

    public boolean hasAnswer() {
        return session.get(OAUTH2_ANSWER) != null;
    }

    public void updateAnswer(String answer) {
        session.put(OAUTH2_ANSWER, answer);
    }

    public void updateAnswer(OAuth2AnswerParameters answer) throws UnsupportedEncodingException, JsonProcessingException {
        updateAnswer(answer.serialize());
    }

}
