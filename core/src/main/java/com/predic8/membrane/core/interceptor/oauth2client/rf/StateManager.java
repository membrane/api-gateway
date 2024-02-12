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

import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.URLParamUtil;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;

public class StateManager {

    private static final SecureRandom sr = new SecureRandom();

    @NotNull
    public static String generateNewState() {
        return new BigInteger(130, sr).toString(32);
    }

    public static String getSecurityTokenFromState(String state2) {
        if (state2 == null)
            throw new RuntimeException("No CSRF token.");

        Map<String, String> param = URLParamUtil.parseQueryString(state2, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);

        if (!param.containsKey("security_token"))
            throw new RuntimeException("No CSRF token.");

        return param.get("security_token");
    }

    public static boolean csrfTokenMatches(Session session, String state2) {
        return Optional.ofNullable(session.get(ParamNames.STATE))
                .filter(o -> Arrays.stream(o.toString().split(SESSION_VALUE_SEPARATOR))
                        .filter(s -> s.equals(state2))
                        .count() == 1
                ).isPresent();
    }
}
