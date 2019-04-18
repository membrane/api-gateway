package com.predic8.membrane.core.interceptor.ntlm;

import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticateResponse;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.auth.NtlmAuthenticator;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.http.HttpClient;
import jcifs.CIFSContext;
import jcifs.context.SingletonContext;
import jcifs.ntlmssp.*;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.NtlmUtil;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "ntlm")
public class NtlmInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(NtlmInterceptor.class);

    NTLMRetriever NTLMRetriever;
    String userHeaderName;
    String passwordHeaderName;
    String domainHeaderName;
    String workstationHeaderName;

    //temporary not configurable


    private CIFSContext context = SingletonContext.getInstance();


    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        if(NTLMRetriever == null)
            NTLMRetriever = new HeaderNTLMRetriever(userHeaderName,passwordHeaderName, domainHeaderName,workstationHeaderName);


    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        HttpClient httpClient = new HttpClient();
        String originalRequestUrl = (exc.getTargetConnection().getSslProvider() != null ? "https" : "http") + "://" + exc.getRequest().getHeader().getHost() + "/";

        if(exc.getResponse().getHeader().getWwwAuthenticate() == null)
            return CONTINUE;

        List<HeaderField> wwwAuthenticate = exc.getResponse().getHeader().getValues(new HeaderName(Header.WWW_AUTHENTICATE));
        if(wwwAuthenticate.stream().filter(h -> h.getValue().toLowerCase().equals("ntlm")).count() == 0)
            return CONTINUE;

        String user = getNTLMRetriever().fetchUsername(exc);
        String pass = getNTLMRetriever().fetchPassword(exc);
        String domain = getNTLMRetriever().fetchDomain(exc) != null ? getNTLMRetriever().fetchDomain(exc) : "";
        String workstation = getNTLMRetriever().fetchWorkstation(exc) != null ? getNTLMRetriever().fetchWorkstation(exc) : "";

        context = context.withCredentials(new NtlmPasswordAuthenticator(domain,user,pass));

        Type1Message t1 = new Type1Message(this.context,0x00080000,null,null);
        /*if ( context.getConfig().getLanManCompatibility() > 2 )
            t1.setFlag(NtlmFlags.NTLMSSP_REQUEST_TARGET, true);*/

        String t1Payload = new String(encode(t1.toByteArray()));

        Exchange reqT1 = new Request.Builder().get(originalRequestUrl).header("Authorization", "NTLM " + t1Payload).buildExchange();
        reqT1.setTargetConnection(exc.getTargetConnection());
        Exchange resT1 = httpClient.call(reqT1);

        //printInterestingThings(resT1);

        String t2Payload = resT1.getResponse().getHeader().getWwwAuthenticate().split(Pattern.quote(" "))[1];
        Type2Message t2 = new Type2Message(decode(t2Payload));

        Type3Message t3 = new Type3Message(context, t2,null,pass,domain,user,workstation,0);
        String t3Payload = new String(encode(t3.toByteArray()));

        Exchange reqT3 = new Request.Builder().get(originalRequestUrl).header("Authorization", "NTLM " + t3Payload).buildExchange();
        reqT3.setTargetConnection(resT1.getTargetConnection());
        Exchange resRess = httpClient.call(reqT3);

        //printInterestingThings(reqT3);

        exc.setResponse(resRess.getResponse());
        exc.setTargetConnection(resRess.getTargetConnection());

        //smbj(exc, httpClient, originalRequestUrl, user, pass, domain); // this was just a test


        return CONTINUE;
    }

    private void smbj(Exchange exc, HttpClient httpClient, String originalRequestUrl, String user, String pass, String domain) throws Exception {
        AuthenticationContext ctx = new AuthenticationContext(user,pass.toCharArray(),domain);
        NtlmAuthenticator ntlmAuthenticator = new NtlmAuthenticator();
        SmbConfig smbConfig = SmbConfig.builder().withAuthenticators(new NtlmAuthenticator.Factory()).withRandomProvider(new SecureRandom()).build();
        ntlmAuthenticator.init(smbConfig);
        AuthenticateResponse t1 = ntlmAuthenticator.authenticate(ctx, null, null);

        String t1Payload = new String(encode(t1.getNegToken()));

        Exchange reqT1 = new Request.Builder().get(originalRequestUrl).header("Authorization", "NTLM " + t1Payload).buildExchange();
        reqT1.setTargetConnection(exc.getTargetConnection());
        Exchange resT1 = httpClient.call(reqT1);
    }

    private byte[] encode(byte[] b){
        return Base64.encode(b);
        //return Base64.getEncoder().encode(b);
    }

    private byte[] decode(String str){
        return Base64.decode(str.getBytes());
        //return Base64.getDecoder().decode(str.getBytes());
    }

    private void printInterestingThings(Exchange exc){
        if(exc.getRequest() != null)
            LOG.info("REQ Authorization: " + exc.getRequest().getHeader().getAuthorization());

        if(exc.getResponse() != null)
            LOG.info("REQ WWW-Authenticate: " + exc.getResponse().getHeader().getWwwAuthenticate());
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
