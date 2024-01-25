package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.jwt.Jwks;
import com.predic8.membrane.core.interceptor.jwt.JwtAuthInterceptor;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

public class UserInfoHandler {
    private static final Logger log = LoggerFactory.getLogger(UserInfoHandler.class);
    private static JwtAuthInterceptor jwtAuthInterceptor;
    private static boolean skip;

    public UserInfoHandler(boolean skip, AuthorizationService auth, Router router) {
        this.skip = skip;

        if (skip) {
            try {
                jwtAuthInterceptor = createJwtAuthInterceptor(auth, router);
            } catch (Exception e) {
                throw new RuntimeException("Could not create JWTAuthInterceptor");
            }
        }
    }

    public static void configureJwtAuthInterceptor(Router router, AuthorizationService auth) throws Exception {
        if (skip) {
            jwtAuthInterceptor = new JwtAuthInterceptor();
            Jwks jwks = new Jwks();
            jwks.setJwks(new ArrayList<>());
            jwks.setJwksUris(auth.getJwksEndpoint());
            jwtAuthInterceptor.setJwks(jwks);
            jwtAuthInterceptor.setExpectedAud("any!!");
            jwtAuthInterceptor.init(router);
        }
    }

    public static void processUserInfo(Map<String, Object> userInfo, Session session, AuthorizationService auth) {
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

    private JwtAuthInterceptor createJwtAuthInterceptor(AuthorizationService auth, Router router) throws Exception {
        var jwtAuthInterceptor = new JwtAuthInterceptor();
        jwtAuthInterceptor.setJwks(createJwks(auth));
        jwtAuthInterceptor.setExpectedAud("any!!");
        jwtAuthInterceptor.init(router);
        return jwtAuthInterceptor;
    }

    private Jwks createJwks(AuthorizationService auth) throws Exception {
        var jwks = new Jwks();
        jwks.setJwks(new ArrayList<>());
        jwks.setJwksUris(auth.getJwksEndpoint());
        return jwks;
    }
}
