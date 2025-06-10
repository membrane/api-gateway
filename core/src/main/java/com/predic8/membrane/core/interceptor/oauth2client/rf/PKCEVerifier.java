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

import com.predic8.membrane.core.interceptor.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * See RFC 7636.
 */
public class PKCEVerifier {
    public static final String SESSION_PARAMETER_VERIFIER = "verifier";

    private static final Logger log = LoggerFactory.getLogger(PKCEVerifier.class);
    private static final String VERIFIER_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
    private static final SecureRandom secureRandom = new SecureRandom();

    final String verifier;

    public PKCEVerifier() {
        verifier = generateNewVerifier();
    }

    public String getId() {
        return verifier.substring(0, 4);
    }

    public static String getVerifier(StateManager state, Session session) {
        String verifiers = session.get(SESSION_PARAMETER_VERIFIER);
        if (verifiers == null) {
            log.warn("No verifier found in session.");
            return null;
        }
        for (String verifier : verifiers.split(SESSION_VALUE_SEPARATOR))
            if (state.getVerifierId().isPresent() && verifier.startsWith(state.getVerifierId().get()))
                return verifier;
        log.warn("No verifier found in session ({}) with id {}.", verifiers, state.getVerifierId().orElse(null));
        return null;
    }

    private static String computeChallenge(String verifier) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA256");
            sha256.update(verifier.getBytes(US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateNewVerifier() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++)
            sb.append(VERIFIER_CHARACTERS.charAt(secureRandom.nextInt(VERIFIER_CHARACTERS.length())));
        return sb.toString();
    }

    public void saveToSession(Session session) {
        String v = verifier;
        if (session.get().containsKey(SESSION_PARAMETER_VERIFIER))
            v = session.get(SESSION_PARAMETER_VERIFIER) + SESSION_VALUE_SEPARATOR + v;
        session.put(SESSION_PARAMETER_VERIFIER, v);
    }

    public String getUrlParams() {
        return "&code_challenge=" + PKCEVerifier.computeChallenge(verifier) + "&code_challenge_method=S256";
    }
}
