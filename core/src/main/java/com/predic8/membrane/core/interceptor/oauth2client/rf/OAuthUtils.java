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
import com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants;
import com.predic8.membrane.core.interceptor.session.Session;

import javax.annotation.Nullable;
import java.util.Optional;

import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.OA2REDIRECT;
import static com.predic8.membrane.core.interceptor.oauth2client.temp.OAuth2Constants.OA2REDIRECT_PREFIX;

public class OAuthUtils {

    /**
     * Tries to avoid very long cookies by dropping all OAUTH2_ANSWERS except the first one.
     * (The SessionManager.mergeCookies produces a value with "{...answer1...},{...answer2...}".
     * We locate the ',' in between the JSON objects and split the string.)
     */
    public static void simplifyMultipleOAuth2Answers(@Nullable Session session) {
        Optional.ofNullable(session)
                .filter(sess -> sess.get(OAuth2Constants.OAUTH2_ANSWER) != null)
                .map(sess -> sess.get(OAuth2Constants.OAUTH2_ANSWER).toString())
                .ifPresent(answer -> keepOnlyFirstOAuthAnswer(session, answer));
    }

    private static void keepOnlyFirstOAuthAnswer(Session session, String answer) {
        var indexOfTopLevelComma = getIndexOfTopLevelComma(answer);

        if (indexOfTopLevelComma < 0) {
            return;
        }

        session.put(OAuth2Constants.OAUTH2_ANSWER, answer.substring(0, indexOfTopLevelComma));
    }

    private static int getIndexOfTopLevelComma(String answer) {
        int curlyBraceLevel = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = 0; i < answer.length(); i++) {
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            char c = answer.charAt(i);
            if (inString) {
                switch (c) {
                    case '\"' -> inString = false;
                    case '\\' -> escapeNext = true;
                }
            } else {
                switch (c) {
                    case '{' -> curlyBraceLevel++;
                    case '}' -> curlyBraceLevel--;
                    case ',' -> {
                        if (curlyBraceLevel == 0)
                            return i;
                    }
                    case '"' -> inString = true;
                }
            }
        }

        return -1;
    }

    public static boolean isOAuth2RedirectRequest(Exchange exc) {
        return exc.getOriginalRequestUri().contains(OA2REDIRECT);
    }

    public static String oa2redictKeyNameInSession(String oa2redirect) {
        return OA2REDIRECT_PREFIX + oa2redirect;
    }
}
