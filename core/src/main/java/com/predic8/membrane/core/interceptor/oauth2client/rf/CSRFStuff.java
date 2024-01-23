package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.interceptor.oauth2.ParamNames;
import com.predic8.membrane.core.interceptor.session.Session;
import com.predic8.membrane.core.util.URLParamUtil;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION_VALUE_SEPARATOR;

// * 2. alles, was CSRF macht
public class CSRFStuff {

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
