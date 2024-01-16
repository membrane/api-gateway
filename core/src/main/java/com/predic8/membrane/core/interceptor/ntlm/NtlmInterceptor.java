/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.ntlm;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.http.*;
import org.apache.http.impl.auth.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

@MCElement(name = "ntlm")
public class NtlmInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(NtlmInterceptor.class);

    NTLMRetriever NTLMRetriever;
    String userHeaderName;
    String passwordHeaderName;
    String domainHeaderName;
    String workstationHeaderName;
    private HttpClient httpClient;

    //temporary not configurable


    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        if(NTLMRetriever == null)
            NTLMRetriever = new HeaderNTLMRetriever(userHeaderName,passwordHeaderName, domainHeaderName,workstationHeaderName);

        httpClient = router.getHttpClientFactory().createClient(null);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        String originalRequestUrl = buildRequestUrl(exc);
        Connection stableConnection = exc.getTargetConnection();

        if(exc.getResponse().getHeader().getWwwAuthenticate() == null)
            return CONTINUE;

        List<HeaderField> wwwAuthenticate = exc.getResponse().getHeader().getValues(new HeaderName(WWW_AUTHENTICATE));
        if(wwwAuthenticate.stream().noneMatch(h -> h.getValue().equalsIgnoreCase("ntlm")))
            return CONTINUE;

        prepareStreamByEmptyingIt(exc);

        String user = getNTLMRetriever().fetchUsername(exc);
        String pass = getNTLMRetriever().fetchPassword(exc);

        if(user == null || pass == null){
            exc.setResponse(Response.unauthorized().header(WWW_AUTHENTICATE,"Realm=ntlm").build());
            return RETURN;
        }

        String domain = getNTLMRetriever().fetchDomain(exc) != null ? getNTLMRetriever().fetchDomain(exc) : null;
        String workstation = getNTLMRetriever().fetchWorkstation(exc) != null ? getNTLMRetriever().fetchWorkstation(exc) : null;

        Exchange resT1 = httpClient.call(createT1MessageRequest(stableConnection, originalRequestUrl));
        prepareStreamByEmptyingIt(resT1);

        Exchange authenticationResult = httpClient.call(createT3MessageRequest(stableConnection, originalRequestUrl, user, pass, domain, workstation, resT1));

        exc.setResponse(authenticationResult.getResponse());
        exc.setTargetConnection(stableConnection);
        return CONTINUE;
    }

    private Exchange createT3MessageRequest(Connection stableConnection, String originalRequestUrl, String user, String pass, String domain, String workstation, Exchange resT1) throws URISyntaxException, NTLMEngineException {
        Exchange reqT3 = new Request.Builder().get(originalRequestUrl).header("Authorization", "NTLM " + NTLMEngineTrampoline.getResponseFor(getT2Payload(resT1), user, pass, workstation, domain)).buildExchange();
        reqT3.getRequest().getHeader().add("Connection","keep-alive");
        reqT3.setTargetConnection(stableConnection);

        return reqT3;
    }

    private Exchange createT1MessageRequest(Connection stableConnection, String originalRequestUrl) throws URISyntaxException, NTLMEngineException {
        Exchange reqT1 = new Request.Builder().get(originalRequestUrl).header("Authorization", "NTLM " + NTLMEngineTrampoline.getResponseFor(null,null,null,null,null)).buildExchange();
        reqT1.getRequest().getHeader().add("Connection","keep-alive");
        reqT1.setTargetConnection(stableConnection);
        return reqT1;
    }

    private String getT2Payload(Exchange resT1) {
        return resT1.getResponse().getHeader().getWwwAuthenticate().split(Pattern.quote(" "))[1];
    }

    private String buildRequestUrl(Exchange exc) {
        return (exc.getTargetConnection().getSslProvider() != null ? "https" : "http") + "://" + exc.getRequest().getHeader().getHost() + exc.getRequestURI();
    }

    private void prepareStreamByEmptyingIt(Exchange exc) {
        try {
            exc.getResponse().getBody().getContent();
        } catch (IOException e) {
            LOG.warn("",e);
        }
    }

    @MCChildElement(order = 1)
    public NtlmInterceptor setNTLMRetriever(NTLMRetriever NTLMRetriever) {
        this.NTLMRetriever = NTLMRetriever;
        return this;
    }

    public NTLMRetriever getNTLMRetriever() {
        return NTLMRetriever;
    }

    @SuppressWarnings("unused")
    public String getUserHeaderName() {
        return userHeaderName;
    }

    @MCAttribute(attributeName = "user")
    public void setUserHeaderName(String userHeaderName) {
        this.userHeaderName = userHeaderName;
    }

    @SuppressWarnings("unused")
    public String getPasswordHeaderName() {
        return passwordHeaderName;
    }

    @MCAttribute(attributeName = "pass")
    public void setPasswordHeaderName(String passwordHeaderName) {
        this.passwordHeaderName = passwordHeaderName;
    }

    @SuppressWarnings("unused")
    public String getDomainHeaderName() {
        return domainHeaderName;
    }

    @MCAttribute(attributeName = "domain")
    public void setDomainHeaderName(String domainHeaderName) {
        this.domainHeaderName = domainHeaderName;
    }

    @SuppressWarnings("unused")
    public String getWorkstationHeaderName() {
        return workstationHeaderName;
    }

    @MCAttribute(attributeName = "workstation")
    public void setWorkstationHeaderName(String workstationHeaderName) {
        this.workstationHeaderName = workstationHeaderName;
    }
}
