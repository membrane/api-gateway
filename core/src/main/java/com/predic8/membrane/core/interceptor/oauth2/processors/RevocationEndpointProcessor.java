/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.processors;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;

public class RevocationEndpointProcessor extends EndpointProcessor {

    private static final Logger log = LoggerFactory.getLogger(RevocationEndpointProcessor.class);

    public RevocationEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        return exc.getRequestURI().startsWith(authServer.getBasePath() + "/oauth2/revoke");
    }

    @Override
    public Outcome process(Exchange exc) {
        try {
            return processInternal(exc);
        } catch (Exception e) {
            log.error("", e);
            internal(true, "revocation-endpoint-processor")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Outcome processInternal(Exchange exc) throws Exception {
        Map<String, String> params = URLParamUtil.getParams(uriFactory, exc, ERROR);

        if (!params.containsKey("token")) {
            exc.setResponse(OAuth2Util.createParameterizedJsonErrorResponse(jsonGen, "error", "invalid_request"));
            return Outcome.RETURN;
        }

        SessionManager.Session session = authServer.getSessionFinder().getSessionForToken(params.get("token"));
        if (session == null) { // token doesnt exist -> token is already invalid
            exc.setResponse(Response
                    .ok()
                    .bodyEmpty()
                    .build());
            return Outcome.RETURN;
        }

        Client client;
        Map<String, String> userAttributes = session.getUserAttributes();
        synchronized (userAttributes) {
            try {
                client = authServer.getClientList().getClient(userAttributes.get(ParamNames.CLIENT_ID));
            } catch (Exception e) {
                // This should never happen
                exc.setResponse(Response
                        .ok()
                        .bodyEmpty()
                        .build());
                return Outcome.RETURN;
            }
        }

        String paramClientId = params.get(ParamNames.CLIENT_ID);
        String paramClientSecret = params.get(ParamNames.CLIENT_SECRET);
        if ((paramClientId != null && !client.getClientId().equals(paramClientId)) || (paramClientSecret != null && !client.getClientSecret().equals(paramClientSecret))) {
            exc.setResponse(OAuth2Util.createParameterizedJsonErrorResponse(jsonGen, "error", "invalid_grant"));
            return Outcome.RETURN;
        }

        try {
            authServer.getTokenGenerator().invalidateToken(params.get("token"), client.getClientId(), client.getClientSecret());
        } catch (Exception e) {
            exc.setResponse(OAuth2Util.createParameterizedJsonErrorResponse(jsonGen, "error", "invalid_grant"));
            return Outcome.RETURN;
        }
        synchronized (session) {
            session.clear();
        }
        synchronized (authServer.getSessionManager()) {
            authServer.getSessionManager().removeSession(session);
        }
        synchronized (authServer.getSessionFinder()) {
            authServer.getSessionFinder().removeSessionForToken(params.get("token"));
        }
        exc.setResponse(Response
                .ok()
                .bodyEmpty()
                .build());
        return Outcome.RETURN;
    }
}
