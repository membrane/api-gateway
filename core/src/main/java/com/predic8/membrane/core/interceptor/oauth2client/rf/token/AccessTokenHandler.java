package com.predic8.membrane.core.interceptor.oauth2client.rf.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.oauth2.OAuth2AnswerParameters;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.AuthorizationService;
import com.predic8.membrane.core.interceptor.oauth2.tokengenerators.JwtGenerator;
import com.predic8.membrane.core.interceptor.oauth2client.rf.JsonUtils;
import com.predic8.membrane.core.interceptor.session.Session;
import org.apache.commons.codec.binary.Base64;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static com.predic8.membrane.core.Constants.USERAGENT;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;

public class AccessTokenHandler {
    private static final Logger log = LoggerFactory.getLogger(AccessTokenHandler.class);

    public static final String OAUTH2_ANSWER = "oauth2Answer";

    private final Session session;
    private JwtKeyCertHandler jwtKeyCertHandler;
    private final AuthorizationService auth;
    private final OAuth2AnswerParameters params;
    private final ObjectMapper om = new ObjectMapper();

    public AccessTokenHandler(Session session, AuthorizationService auth) {
        this.session = session;
        this.auth = auth;

        try {
            this.params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));
        } catch (IOException e) {
            throw new RuntimeException("Could not deserialize OAuth2 answer");
        }
    }

    private void refreshAccessToken(Session session) throws Exception {
        if (!refreshingOfAccessTokenIsNeeded(session))
            return;

        Exchange refreshTokenExchange = applyAuth(new Request.Builder()
                        .post(auth.getTokenEndpoint())
                        .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                        .header(ACCEPT, APPLICATION_JSON)
                        .header(USER_AGENT, USERAGENT),
                "grant_type=refresh_token"
                        + "&refresh_token=" + params.getRefreshToken())
                .buildExchange();

        Response refreshTokenResponse = auth.doRequest(refreshTokenExchange);
        if (!refreshTokenResponse.isOk()) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode from authorization server for refresh token request: " + refreshTokenResponse.getStatusCode());
        }
        if (!JsonUtils.isJson(refreshTokenResponse))
            throw new RuntimeException("Refresh Token response is no JSON.");

        @SuppressWarnings("unchecked")
        Map<String, Object> json = om.readValue(refreshTokenResponse.getBodyAsStreamDecoded(), Map.class);

        if (json.get("access_token") == null || json.get("refresh_token") == null) {
            refreshTokenResponse.getBody().read();
            throw new RuntimeException("Statuscode was ok but no access_token and refresh_token was received: " + refreshTokenResponse.getStatusCode());
        }

        params.setAccessToken((String) json.get("access_token"));
        params.setRefreshToken((String) json.get("refresh_token"));
        params.setExpiration(numberToString(json.get("expires_in")));
        LocalDateTime now = LocalDateTime.now();
        params.setReceivedAt(now.withSecond(now.getSecond() / 30 * 30).withNano(0));
        if (json.containsKey("id_token")) {
            if (idTokenIsValid((String) json.get("id_token")))
                params.setIdToken((String) json.get("id_token"));
            else
                params.setIdToken("INVALID");
        }

        session.put(OAUTH2_ANSWER, params.serialize());
    }

    private String numberToString(Object number) {
        return switch (number) {
            case null -> null;
            case Integer ignored -> number.toString();
            case Long ignored -> number.toString();
            case Double ignored -> number.toString();
            case String s -> s;
            default -> {
                log.warn("Unhandled number type " + number.getClass().getName());
                yield null;
            }
        };
    }

    private boolean refreshingOfAccessTokenIsNeeded(Session session) throws IOException {
        if (session.get(OAUTH2_ANSWER) == null)
            return false;

        OAuth2AnswerParameters oauth2Params = OAuth2AnswerParameters.deserialize(session.get(OAUTH2_ANSWER));

        var expiration = oauth2Params.getExpiration();

        if (isNullOrEmpty(oauth2Params.getRefreshToken(), expiration)) {
            return false;
        }

        var expirationTime = oauth2Params.getReceivedAt().plusSeconds(Long.parseLong(expiration)).minusSeconds(5);

        return LocalDateTime.now().isAfter(expirationTime);
    }

    private boolean isNullOrEmpty(String... values) {
        return Arrays.stream(values).anyMatch(value -> value == null || value.isEmpty());
    }

    private boolean idTokenIsValid(String idToken) {
        try {
            JwtGenerator.getClaimsFromSignedIdToken(idToken, auth.getIssuer(), auth.getClientId(), auth.getJwksEndpoint(), auth);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Request.Builder applyAuth(Request.Builder requestBuilder, String body) {

        if (auth.isUseJWTForClientAuth()) {
            body += "&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" +
                    "&client_assertion=" + createClientToken();
        }

        String clientSecret = auth.getClientSecret();

        if (clientSecret != null) {
            requestBuilder
                    .header(AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((auth.getClientId() + ":" + clientSecret).getBytes())))
                    .body(body);
        } else {
            requestBuilder.body(body + "&client_id" + auth.getClientId());
        }

        return requestBuilder;
    }

    private String createClientToken() {
        try {
            String jwtSub = auth.getClientId();
            String jwtAud = auth.getTokenEndpoint();

            NumericDate expiration = NumericDate.now();
            expiration.addSeconds(300);

            // see https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-certificate-credentials
            JwtClaims jwtClaims = new JwtClaims();
            jwtClaims.setSubject(jwtSub);
            jwtClaims.setAudience(jwtAud);
            jwtClaims.setIssuer(jwtClaims.getSubject());
            jwtClaims.setJwtId(UUID.randomUUID().toString());
            jwtClaims.setIssuedAtToNow();
            jwtClaims.setExpirationTime(expiration);
            jwtClaims.setNotBeforeMinutesInThePast(2f);

            return jwtKeyCertHandler.generateSignedJWS(jwtClaims);
        } catch (JoseException | MalformedClaimException e) {
            throw new RuntimeException(e);
        }
    }


}
