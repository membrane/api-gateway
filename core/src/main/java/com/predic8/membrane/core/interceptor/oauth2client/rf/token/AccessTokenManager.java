package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;

public class AccessTokenManager {

    public static boolean idTokenIsValid(String idToken, AuthorizationService auth) {
        //TODO maybe change this to return claims and also save them in the oauth2AnswerParameters
        try {
            JwtGenerator.getClaimsFromSignedIdToken(idToken, auth.getIssuer(), auth.getClientId(), auth.getJwksEndpoint(), auth);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void revalidateToken() {

    }

    public void refreshToken() {

    }

}
