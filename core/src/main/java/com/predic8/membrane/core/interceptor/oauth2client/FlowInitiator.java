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
package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.oauth2.authorizationservice.FlowContext;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.predic8.membrane.core.http.Header.LOCATION;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@MCElement(name = "flowInitiator")
public class FlowInitiator extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowInitiator.class.getName());

    private String triggerFlow;
    private String defaultFlow;
    private String afterLoginUrl;
    private OAuth2Resource2Interceptor oauth2;
    private List<LoginParameter> loginParameters = new ArrayList<>();
    private boolean logoutBeforeFlow = true;

    public String getTriggerFlow() {
        return triggerFlow;
    }

    @MCAttribute
    public void setTriggerFlow(String triggerFlow) {
        this.triggerFlow = triggerFlow;
    }

    public OAuth2Resource2Interceptor getOauth2() {
        return oauth2;
    }

    @MCAttribute
    public void setOauth2(OAuth2Resource2Interceptor oauth2) {
        this.oauth2 = oauth2;
    }

    public String getDefaultFlow() {
        return defaultFlow;
    }

    @MCAttribute
    public void setDefaultFlow(String defaultFlow) {
        this.defaultFlow = defaultFlow;
    }

    public String getAfterLoginUrl() {
        return afterLoginUrl;
    }

    @MCAttribute
    public void setAfterLoginUrl(String afterLoginUrl) {
        this.afterLoginUrl = afterLoginUrl;
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return handleRequestInternal(exc);
        } catch (Exception e) {
            log.error("", e);
            ProblemDetails.internal(router.isProduction(),getDisplayName())
                    .detail("Error initiating OAuth2 flow!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    public Outcome handleRequestInternal(Exchange exc) throws Exception {
        List<HeaderField> values = null;
        if (logoutBeforeFlow) {
            // remove session
            exc.setResponse(Response.ok().build());
            oauth2.logOutSession(exc);
            values = exc.getResponse().getHeader().getValues(new HeaderName("Set-Cookie"));

            exc.getRequest().getHeader().removeFields("Cookie");
        }

        var params = URLParamUtil.getParams(new URIFactory(), exc, URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR);
        exc.setProperty("loginParameters", LoginParameter.mergeParams(params, loginParameters));

        // create new response redirecting user to new flow
        exc.getRequest().setUri(afterLoginUrl);
        exc.setOriginalRequestUri(afterLoginUrl);

        oauth2.respondWithRedirect(exc, FlowContext.fromConfig(defaultFlow, triggerFlow));
        oauth2.getSessionManager().postProcess(exc); // required to create a session cookie
        if (logoutBeforeFlow) {
            values.forEach(header -> exc.getResponse().getHeader().add(header));
        }

        exc.getResponse().getHeader().setValue(LOCATION, replaceFlow(exc.getResponse().getHeader().getFirstValue(LOCATION)));
        exc.getResponse().setBodyContent(replaceFlow(exc.getResponse().getBodyAsStringDecoded()).getBytes(UTF_8));

        return Outcome.RETURN;
    }

    private String replaceFlow(String text) {
        return text.replaceAll("/" + defaultFlow + "/", "/" + triggerFlow + "/");
    }

    public List<LoginParameter> getLoginParameters() {
        return loginParameters;
    }

    @MCChildElement
    public void setLoginParameters(List<LoginParameter> loginParameters) {
        this.loginParameters = loginParameters;
    }

    public boolean isLogoutBeforeFlow() {
        return logoutBeforeFlow;
    }

    @MCAttribute
    public void setLogoutBeforeFlow(boolean logoutBeforeFlow) {
        this.logoutBeforeFlow = logoutBeforeFlow;
    }
}
