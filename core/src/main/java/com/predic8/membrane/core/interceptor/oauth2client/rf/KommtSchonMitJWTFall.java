package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2Statistics;
import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2client.rf.token.AccessTokenRevalidator;
import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;

public class KommtSchonMitJWTFall {
    private static final Logger log = LoggerFactory.getLogger(KommtSchonMitJWTFall.class);

    public static boolean userInfoIsNullAndShouldRedirect(
            UserInfoHandler userInfoHandler,
            Session session,
            Exchange exc,
            OAuth2Statistics statistics,
            AccessTokenRevalidator accessTokenRevalidator,
            AuthorizationService authService
    ) throws Exception {
        if (!userInfoHandler.isSkip() && !session.isVerified()) {
            String auth = exc.getRequest().getHeader().getFirstValue(AUTHORIZATION);
            if (auth != null && isBearer(auth)) {
                session.put(ParamNames.ACCESS_TOKEN, auth.substring(7));

                OAuth2AnswerParameters oauth2Answer = new OAuth2AnswerParameters();
                oauth2Answer.setAccessToken(auth.substring(7));
                oauth2Answer.setTokenType("Bearer");

                Map<String, Object> userinfo =
                        accessTokenRevalidator.revalidate(session, statistics);

                if (logUserInfoIsNull(userinfo)) {
                    return true;
//                    return respondWithRedirect(exc);
                }

                oauth2Answer.setUserinfo(userinfo);

                session.setOAuth2Answer(oauth2Answer.serialize());
                userInfoHandler.processUserInfo(userinfo, session, authService);
            }
        }
        return false;
    }

    private static boolean logUserInfoIsNull(Map<String, Object> userinfo) {
        if (userinfo == null) {
            log.debug("userinfo is null, redirecting.");
            return true;
        }
        return false;
    }

    private static boolean isBearer(String auth) {
        return auth.substring(0, 7).equalsIgnoreCase("Bearer ");
    }
}
