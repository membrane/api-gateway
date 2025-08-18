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

package com.predic8.membrane.core.interceptor.oauth2.authorizationservice;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.session.Session;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.oauth2.OAuth2Util.urlencode;
import static com.predic8.membrane.core.interceptor.oauth2client.rf.StateManager.getValueFromState;

/**
 * This is a parametrization of the B2C Token endpoint.
 *
 * This object can be null. If not null, <code>defaultFlow</code> will be replaced by <code>triggerFlow</code> in the
 * Token Endpoint URL.
 *
 * If set, the FlowContext represents the information, that a non-default B2C flow should be used.
 *
 * The FlowContext is passed
 * <ul>
 *     <li><b>on the current Exchange</b> or <b>as a method parameter</b> to carry the information within a Java caller
 *     context</li>
 *     <li><b>in the OAuth2 state parameter</b> to carry the information during a (re)login process. (Note that the
 *     session and oauth2 state might temporarily disagree, especially when an OAuth2 flow is executed in parallel or
 *     temporarily paused due to user interaction)</li>
 *     <li><b>via a session entry</b> to carry the information which flow the current refresh token belongs to.</li>
 * </ul>
 *
 */
public class FlowContext {

    private static final Logger log = LoggerFactory.getLogger(FlowContext.class);

    public final String defaultFlow;
    public final String triggerFlow;

    private FlowContext(String defaultFlow, String triggerFlow) {
        this.defaultFlow = defaultFlow;
        this.triggerFlow = triggerFlow;
    }

    public static FlowContext fromExchange(Exchange exc) {
        FlowContext fc = exc.getPropertyOrNull("flowContext", FlowContext.class);
        log.debug("FlowContext from exchange = {}", fc);
        return fc;
    }

    public static FlowContext fromSession(Session session) {
        String fcd = session.get("flowContextDefault");
        String fct = session.get("flowContextTrigger");
        if (fcd == null) {
            log.debug("FlowContext from session = null");
            return null;
        }
        FlowContext fc = new FlowContext(fcd, fct);
        log.debug("FlowContext from session = {}", fc);
        return fc;
    }

    public static void applyToSession(FlowContext flowContext, Session session) {
        log.debug("FlowContext to session = {}", flowContext);
        if (flowContext == null) {
            session.remove("flowContextDefault", "flowContextTrigger");
            return;
        }
        session.put("flowContextDefault", flowContext.defaultFlow);
        session.put("flowContextTrigger", flowContext.triggerFlow);
    }

    public static @NotNull String toUrlParam(FlowContext fc) {
        log.debug("FlowContext to URL = {}", fc);
        return fc != null ? "%26fcd%3D" + urlencode(fc.defaultFlow) + "%26fct%3D" + urlencode(fc.triggerFlow) : "";
    }

    public static FlowContext fromUrlParam(String params) {
        var fcD = getValueFromState(params, "fcd");
        var fcT = getValueFromState(params, "fct");
        if (fcD != null) {
            FlowContext flowContext = new FlowContext(fcD, fcT);
            log.debug("FlowContext from URL = {}", flowContext);
            return flowContext;
        } else {
            log.debug("FlowContext from URL = null");
            return null;
        }
    }

    public static FlowContext fromConfig(String defaultFlow, String triggerFlow) {
        FlowContext fc = new FlowContext(defaultFlow, triggerFlow);
        log.debug("FlowContext from config = {}", fc);
        return fc;
    }

    @Override
    public String toString() {
        return "FlowContext{" +
                "defaultFlow='" + defaultFlow + '\'' +
                ", triggerFlow='" + triggerFlow + '\'' +
                '}';
    }
}
