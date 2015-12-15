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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apimanagement.rateLimiter.AMRateLimiter;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@MCElement(name="apiManagement")
public class ApiManagementInterceptor extends AbstractInterceptor {

    private static Logger log = LogManager.getLogger(ApiManagementInterceptor.class);
    private StaticPolicyDecisionPoint staticPolicyDecisionPoint;
    private AMRateLimiter amRli = null;


    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        Object amcObj = router.getBeanFactory().getBean("amc");
        if(amcObj == null){
            log.error("ApiManagementConfiguration not available. Define it as a spring bean in proxies.xml");
        }
        ApiManagementConfiguration amc = (ApiManagementConfiguration) amcObj;
        setStaticPolicyDecisionPoint(new StaticPolicyDecisionPoint(amc));
        if(amRli != null){
            amRli.setAmc(amc);
        }
    }


    public StaticPolicyDecisionPoint getStaticPolicyDecisionPoint() {
        return staticPolicyDecisionPoint;
    }

    public void setStaticPolicyDecisionPoint(StaticPolicyDecisionPoint staticPolicyDecisionPoint) {
        this.staticPolicyDecisionPoint = staticPolicyDecisionPoint;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        String key = exc.getRequest().getHeader().getFirstValue("Authorization");
        if(key == null)
        {
            setResponseNoAuthKey(exc);
            return Outcome.RETURN;
        }
        exc.setProperty(Exchange.API_KEY, key);
        AuthorizationResult auth = staticPolicyDecisionPoint.getAuthorization(exc,key);
        if(auth.isAuthorized())
        {
            if(amRli != null){
                return amRli.handleRequest(exc);
            }
            return Outcome.CONTINUE;
        }
        else
        {
            setResponsePolicyDenied(exc, auth);
            return Outcome.RETURN;
        }


    }

    private Response buildResponse(int code, String msg, ByteArrayOutputStream baos){
        Response resp = new Response.ResponseBuilder().status(code,msg).contentType("application/json").body(baos.toByteArray()).build();
        return resp;
    }

    private ByteArrayOutputStream buildJson(int code, String msg){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator jgen = null;
        try {
            jgen = new JsonFactory().createGenerator(os);
            jgen.writeStartObject();
            jgen.writeObjectField("Statuscode", code);
            jgen.writeObjectField("Message", msg);
            jgen.writeEndObject();
            jgen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return os;
    }

    private void setResponsePolicyDenied(Exchange exc, AuthorizationResult auth) {
        int code = 400;
        String msg = "Bad request";
        Response resp = buildResponse(code,msg,buildJson(code,msg));
        exc.setResponse(resp);
    }

    private void setResponseNoAuthKey(Exchange exc) {
        int code = 401;
        String msg = "Unauthorized";
        Response resp = buildResponse(code,msg,buildJson(code,msg));
        exc.setResponse(resp);
    }

    public AMRateLimiter getAmRli() {
        return amRli;
    }

    @MCChildElement
    public void setAmRli(AMRateLimiter amRli) {
        this.amRli = amRli;
        try {
            //this.amRli.init(this.getRouter());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
