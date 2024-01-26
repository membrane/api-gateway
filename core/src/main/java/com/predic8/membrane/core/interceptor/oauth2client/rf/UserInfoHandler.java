package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.jwt.Jwks;
import com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;

import java.util.ArrayList;
import java.util.Map;

public class UserInfoHandler {
    private JwtAuthInterceptor jwtAuthInterceptor;
    private boolean skip;

    private AuthorizationService auth;
    private Router router;

    public void init(AuthorizationService auth, Router router) {
        this.auth = auth;
        this.router = router;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
        if (skip) {
            try {
                this.jwtAuthInterceptor = createJwtAuthInterceptor();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            this.jwtAuthInterceptor = null;
        }
    }

    public void processUserInfo(Map<String, Object> userInfo, Session session, AuthorizationService auth) {
        if (!userInfo.containsKey(auth.getSubject()))
            throw new RuntimeException("User object does not contain " + auth.getSubject() + " key.");

        Map<String, Object> userAttributes = session.get();
        String userIdPropertyFixed = auth.getSubject().substring(0, 1).toUpperCase() + auth.getSubject().substring(1);
        String username = (String) userInfo.get(auth.getSubject());
        userAttributes.put("headerX-Authenticated-" + userIdPropertyFixed, username);

        session.authorize(username);
    }

    public JwtAuthInterceptor getJwtAuthInterceptor() {
        return jwtAuthInterceptor;
    }

    private JwtAuthInterceptor createJwtAuthInterceptor() throws Exception {
        var jwtAuthInterceptor = new JwtAuthInterceptor();

        jwtAuthInterceptor.setJwks(createJwks());
        jwtAuthInterceptor.setExpectedAud("any!!");
        jwtAuthInterceptor.init(router);

        return jwtAuthInterceptor;
    }

    private Jwks createJwks() throws Exception {
        var jwks = new Jwks();

        jwks.setJwks(new ArrayList<>());
        jwks.setJwksUris(auth.getJwksEndpoint());

        return jwks;
    }
}
