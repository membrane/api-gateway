/* Copyright 2024 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.FlowContext;
import com.predic8.membrane.core.interceptor.session.Session;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static com.predic8.membrane.core.http.Response.badRequest;
import static com.predic8.membrane.core.interceptor.oauth2.OAuth2Util.urlencode;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.FlowContext.fromUrlParam;
import static com.predic8.membrane.core.interceptor.oauth2.authorizationservice.FlowContext.toUrlParam;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.OAuth2CallbackRequestHandler.*;
import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.parseQueryString;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;

public class StateManager {
    private static final String SESSION_PARAMETER_STATE = "state";

    private static final Logger log = LoggerFactory.getLogger(StateManager.class);
    private static final SecureRandom sr = new SecureRandom();

    private final String securityToken;
    private final String verifierId;
    private final FlowContext flowContext;

    public StateManager(PKCEVerifier pkceVerifier, FlowContext flowContext) {
        securityToken = generateNewState();
        verifierId = pkceVerifier.getId();
        this.flowContext = flowContext;
    }

    public StateManager(String stateFromUri) {
        securityToken = getValueFromState(stateFromUri, "security_token");
        verifierId = getValueFromState(stateFromUri, "verifierId");
        flowContext = fromUrlParam(stateFromUri);
    }


    @NotNull
    public static String generateNewState() {
        return new BigInteger(130, sr).toString(32);
    }

    public static String getValueFromState(String state, String key) {
        if (state == null)
            throw new RuntimeException("State is null, No "+key+".");

        Map<String, String> param = parseQueryString(decode(state, UTF_8), ERROR);

        return param.get(key);
    }

    public static void verifyCsrfToken(Session session, StateManager stateFromUri) throws OAuth2Exception {

        if (!matchesCsrfToken(stateFromUri, session.get(SESSION_PARAMETER_STATE))) {
            if (session.isNew()) {
                throw new OAuth2Exception(
                        MEMBRANE_MISSING_SESSION,
                        MEMBRANE_MISSING_SESSION_DESCRIPTION,
                        badRequest().body(MEMBRANE_MISSING_SESSION_DESCRIPTION).build());
            } else if (!StateManager.hasState(session)) {
                throw new OAuth2Exception(
                        MEMBRANE_CSRF_TOKEN_MISSING_IN_SESSION,
                        MEMBRANE_CSRF_TOKEN_MISSING_IN_SESSION_DESCRIPTION,
                        badRequest().body(MEMBRANE_CSRF_TOKEN_MISSING_IN_SESSION_DESCRIPTION).build());
            } else {
                log.warn("Token from Session: '{}', Token from URI: '{}'", session.get(SESSION_PARAMETER_STATE), stateFromUri.getSecurityToken());
                throw new OAuth2Exception(
                        MEMBRANE_CSRF_TOKEN_MISMATCH,
                        MEMBRANE_CSRF_TOKEN_MISMATCH_DESCRIPTION,
                        badRequest().body(MEMBRANE_CSRF_TOKEN_MISMATCH_DESCRIPTION).build());
            }
        }

        // state in session can be "merged" -> save the selected state in session overwriting the possibly merged value
        if (!(session.get(SESSION_PARAMETER_STATE).equals(stateFromUri.getSecurityToken()))) {
            log.warn("Replacing saved state '{}' with '{}'", session.get(SESSION_PARAMETER_STATE), stateFromUri.getSecurityToken());
        }
        session.put(SESSION_PARAMETER_STATE, stateFromUri.getSecurityToken());
    }

    private static boolean matchesCsrfToken(StateManager stateFromUri, Object stateFromSession) {
        return Optional.ofNullable(stateFromSession)
                .filter(o -> hasExactlyOneMatchingToken(stateFromUri, o))
                .isPresent();
    }

    private static boolean hasExactlyOneMatchingToken(StateManager stateFromUri, Object stateFromSession) {
        return Arrays.stream(stateFromSession.toString().split(SESSION_VALUE_SEPARATOR))
                .filter(s -> s.equals(stateFromUri.getSecurityToken()))
                .count() == 1;
    }

    public static boolean hasState(Session session) {
        return session.get().containsKey(SESSION_PARAMETER_STATE);
    }

    public String buildStateParameter(Exchange exchange) {
        return "&state=security_token%3D" + securityToken + "%26url%3D" + urlencode(exchange.getRequestURI())
                + "%26verifierId%3D" + verifierId + toUrlParam(flowContext);
    }

    public void saveToSession(Session session) {
        String s = securityToken;
        if (session.get().containsKey(SESSION_PARAMETER_STATE))
            s = session.get(SESSION_PARAMETER_STATE) + SESSION_VALUE_SEPARATOR + s;
        session.put(SESSION_PARAMETER_STATE, s);
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public Optional<String> getVerifierId() {
        return Optional.ofNullable(verifierId);
    }

    public FlowContext getFlowContext() {
        return flowContext;
    }

    @Override
    public String toString() {
        return "StateManager{" +
                "securityToken='" + securityToken + '\'' +
                ", verifierId='" + verifierId + '\'' +
                ", flowContext=" + flowContext +
                '}';
    }

}
