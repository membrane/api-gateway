/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.core.interceptor.apimanagement;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

@MCElement(name="apiManagement")
public class ApiManagementInterceptor extends AbstractInterceptor {

    private static Logger log = LogManager.getLogger(ApiManagementInterceptor.class);
    private StaticPolicyDecisionPoint staticPolicyDecisionPoint;


    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        staticPolicyDecisionPoint.init(router);
    }

    public StaticPolicyDecisionPoint getStaticPolicyDecisionPoint() {
        return staticPolicyDecisionPoint;
    }

    @MCChildElement
    public void setStaticPolicyDecisionPoint(StaticPolicyDecisionPoint staticPolicyDecisionPoint) {
        this.staticPolicyDecisionPoint = staticPolicyDecisionPoint;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String key = exc.getRequest().getHeader().getFirstValue("Authorization");
        if(key == null)
        {
            System.out.println("No auth key");
            setResponseNoAuthKey(exc);
            return Outcome.RETURN;
        }
        AuthorizationResult auth = staticPolicyDecisionPoint.getAuthorization(exc,key);
        if(auth.isAuthorized())
        {
            System.out.println("Granted");
            return Outcome.CONTINUE;
        }
        else
        {
            System.out.println("Denied: " + auth.getReason());
            setResponsePolicyDenied(exc, auth);
            return Outcome.RETURN;
        }


    }

    private void setResponsePolicyDenied(Exchange exc, AuthorizationResult auth) {
    }

    private void setResponseNoAuthKey(Exchange exc) {

    }
}
