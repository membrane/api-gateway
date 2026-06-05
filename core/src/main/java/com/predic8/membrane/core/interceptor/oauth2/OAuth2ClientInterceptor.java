package com.predic8.membrane.core.interceptor.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.net.URLEncoder.encode;
import static com.predic8.membrane.core.exceptions.ProblemDetails.gateway;
import static com.predic8.membrane.core.http.Header.ACCEPT;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.oauth2.OAuth2Util.urlencode;
import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getEncoder;

/**
 * @description Obtains an OAuth2 access token using the client credentials flow and forwards the request with a Bearer token.
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - oauth2Client:
 *         tokenUrl: https://auth.example.com/oauth2/token
 *         clientId: gateway
 *         clientSecret: secret
 *         scope: read write
 *   target:
 *     url: https://api.example.com
 * </code></pre>
 */
@MCElement(name="oauth2Client")
public class OAuth2ClientInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ClientInterceptor.class);

    private String tokenUrl;

    private String clientId;

    private String clientSecret;

    private String scope;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient;
    private final Object tokenLock = new Object();

    private volatile String cachedAccessToken;
    private volatile long cachedAccessTokenValidUntilEpochMillis;

    @Override
    public void init() {
        name = "OAuth2 Client";
        setAppliedFlow(REQUEST_FLOW);

        httpClient = router.getHttpClientFactory().createClient(router.getHttpClientConfig());
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            String token = getAccessToken();
            exc.getRequest().getHeader().setValue(AUTHORIZATION, "Bearer " + token);
            return CONTINUE;
        } catch (Exception e) {
            log.warn("Could not obtain OAuth2 access token from {}: {}", tokenUrl, e.getMessage());
            log.debug("OAuth2 token request failed.", e);
            gateway(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Bad Gateway")
                    .status(502)
                    .addSubSee("oauth2-token")
                    .detail("Could not obtain an OAuth2 access token.")
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private String getAccessToken() throws Exception {
        if (hasValidCachedToken()) {
            return cachedAccessToken;
        }

        synchronized (tokenLock) {
            if (hasValidCachedToken()) {
                return cachedAccessToken;
            }
            return fetchAccessToken();
        }
    }

    private boolean hasValidCachedToken() {
        return cachedAccessToken != null && currentTimeMillis() < cachedAccessTokenValidUntilEpochMillis;
    }

    private String fetchAccessToken() throws Exception {
        Exchange tokenExchange = new Request.Builder()
                .post(tokenUrl)
                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .header(ACCEPT, APPLICATION_JSON)
                .header(AUTHORIZATION, buildBasicAuthorization())
                .body(buildTokenRequestBody())
                .buildExchange();

        httpClient.call(tokenExchange);

        var response = tokenExchange.getResponse();
        String responseBody = response.getBodyAsStringDecoded();
        if (response.getStatusCode() != 200) {
            throw new IllegalStateException("Authorization server returned status " + response.getStatusCode() + ".");
        }

        var responseJson = objectMapper.readTree(responseBody);
        String token = responseJson.path("access_token").asText(null);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Authorization server did not return an access token.");
        }

        updateTokenCache(token, responseJson.path("expires_in").asLong(-1));
        return token;
    }

    private void updateTokenCache(String token, long expiresInSeconds) {
        if (expiresInSeconds <= 0) {
            cachedAccessToken = null;
            cachedAccessTokenValidUntilEpochMillis = 0;
            log.debug("Token response from {} has no usable expires_in. Token will not be cached.", tokenUrl);
            return;
        }

        // Refresh slightly before expiry to avoid sending a token that expires mid-request.
        long refreshBufferSeconds = Math.min(30, max(1, expiresInSeconds / 10));

        cachedAccessToken = token;
        cachedAccessTokenValidUntilEpochMillis = currentTimeMillis() + max(1, expiresInSeconds - refreshBufferSeconds) * 1000;
    }

    private String buildTokenRequestBody() {
        StringBuilder body = new StringBuilder("grant_type=client_credentials");
        appendParam(body, "scope", scope);
        return body.toString();
    }

    private void appendParam(StringBuilder body, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        body.append("&")
                .append(name)
                .append("=")
                .append(urlencode(value));
    }

    private String buildBasicAuthorization() {
        String credentials = encodeClientCredential(clientId) + ":" + encodeClientCredential(clientSecret);
        return "Basic " + getEncoder().encodeToString(credentials.getBytes(UTF_8));
    }

    private String encodeClientCredential(String value) {
        return encode(value, UTF_8);
    }

    /**
     * @description The token endpoint used to obtain the OAuth2 access token.
     * @required
     * @example https://auth.example.com/oauth2/token
     */
    @MCAttribute
    @Required
    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    /**
     * @description The OAuth2 client id used for the token request.
     * @required
     * @example gateway
     */
    @MCAttribute
    @Required
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * @description The OAuth2 client secret used for the token request.
     * @required
     * @example secret
     */
    @MCAttribute
    @Required
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * @description Space-separated scopes requested for the access token.
     * @example read write
     */
    @MCAttribute
    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getScope() {
        return scope;
    }
}
