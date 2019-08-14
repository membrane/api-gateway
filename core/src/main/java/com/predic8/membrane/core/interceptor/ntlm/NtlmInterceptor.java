package com.predic8.membrane.core.interceptor.ntlm;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.apache.http.impl.auth.NTLMEngineException;
import org.apache.http.impl.auth.NTLMEngineTrampoline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

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


    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        httpClient = createClient();

        String originalRequestUrl = buildRequestUrl(exc);
        Connection stableConnection = exc.getTargetConnection();

        if(exc.getResponse().getHeader().getWwwAuthenticate() == null)
            return CONTINUE;

        List<HeaderField> wwwAuthenticate = exc.getResponse().getHeader().getValues(new HeaderName(Header.WWW_AUTHENTICATE));
        if(wwwAuthenticate.stream().filter(h -> h.getValue().toLowerCase().equals("ntlm")).count() == 0)
            return CONTINUE;

        prepareStreamByEmptyingIt(exc);

        String user = getNTLMRetriever().fetchUsername(exc);
        String pass = getNTLMRetriever().fetchPassword(exc);

        if(user == null || pass == null){
            exc.setResponse(Response.unauthorized().header(Header.WWW_AUTHENTICATE,"Realm=ntlm").build());
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
        Exchange reqT3 = new Request.Builder().get(originalRequestUrl).header("Authorization", "NTLM " + NTLMEngineTrampoline.getResponseFor(getT2Payload(resT1),user,pass,domain,workstation)).buildExchange();
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
        return (exc.getTargetConnection().getSslProvider() != null ? "https" : "http") + "://" + exc.getRequest().getHeader().getHost() + "/";
    }

    private void prepareStreamByEmptyingIt(Exchange exc) {
        try {
            exc.getResponse().getBody().getContent();
        } catch (IOException e) {
            LOG.warn("",e);
        }
    }

    private HttpClient createClient() {
        HttpClientConfiguration configuration = new HttpClientConfiguration();
        return new HttpClient(configuration);
    }

    @MCChildElement(order = 1)
    public NtlmInterceptor setNTLMRetriever(NTLMRetriever NTLMRetriever) {
        this.NTLMRetriever = NTLMRetriever;
        return this;
    }

    public NTLMRetriever getNTLMRetriever() {
        return NTLMRetriever;
    }

    public String getUserHeaderName() {
        return userHeaderName;
    }

    @MCAttribute(attributeName = "user")
    public void setUserHeaderName(String userHeaderName) {
        this.userHeaderName = userHeaderName;
    }

    public String getPasswordHeaderName() {
        return passwordHeaderName;
    }

    @MCAttribute(attributeName = "pass")
    public void setPasswordHeaderName(String passwordHeaderName) {
        this.passwordHeaderName = passwordHeaderName;
    }

    public String getDomainHeaderName() {
        return domainHeaderName;
    }

    @MCAttribute(attributeName = "domain")
    public void setDomainHeaderName(String domainHeaderName) {
        this.domainHeaderName = domainHeaderName;
    }

    public String getWorkstationHeaderName() {
        return workstationHeaderName;
    }

    @MCAttribute(attributeName = "workstation")
    public void setWorkstationHeaderName(String workstationHeaderName) {
        this.workstationHeaderName = workstationHeaderName;
    }
}
