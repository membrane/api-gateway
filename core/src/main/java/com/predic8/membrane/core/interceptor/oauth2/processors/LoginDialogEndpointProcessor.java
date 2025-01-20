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
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.oauth2.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public class LoginDialogEndpointProcessor extends EndpointProcessor {

    private static final Logger log = LoggerFactory.getLogger(LoginDialogEndpointProcessor.class);
    private final LoginDialog loginDialog;

    public LoginDialogEndpointProcessor(OAuth2AuthorizationServerInterceptor authServer) {
        super(authServer);
        if(authServer.isLoginViewDisabled()) {
            loginDialog = null;
            return;
        }
        loginDialog = new LoginDialog(authServer.getUserDataProvider(), null, authServer.getSessionManager(), authServer.getAccountBlocker(), authServer.getLocation(), authServer.getBasePath(), authServer.getPath(), authServer.isExposeUserCredentialsToSession(), authServer.getMessage());
        try {
            loginDialog.init(authServer.getRouter());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isResponsible(Exchange exc) {
        URI uri = uriFactory.createWithoutException(exc.getRequest().getUri());
        return uri.getPath().startsWith(authServer.getBasePath() + authServer.getPath()) && authServer.getSessionManager().getSession(exc) != null; // TODO: check session for parameters
    }

    @Override
    public Outcome process(Exchange exc) {
        try {
            loginDialog.handleLoginRequest(exc);
        } catch (Exception e) {
            log.error(e.getMessage(),e);
            internal(true,"login-dialog-endpoint-processor")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        return Outcome.RETURN;
    }


}
