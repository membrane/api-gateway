package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.session.Session;

import java.util.List;

import static com.predic8.membrane.core.interceptor.session.SessionManager.SESSION;

@MCElement(name = "flowInitiator")
public class UserFlow extends AbstractInterceptor {
    private String triggerFlow;
    private String defaultFlow;
    private String afterLoginUrl;
    private OAuth2Resource2Interceptor oauth2;

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
    public Outcome handleRequest(Exchange exc) throws Exception {
        // remove session
        exc.setResponse(Response.ok().build());
        oauth2.logOutSession(exc);
        List<HeaderField> values = exc.getResponse().getHeader().getValues(new HeaderName("Set-Cookie"));

        exc.getRequest().getHeader().removeFields("Cookie");


        // create new response redirecting user to new flow
        exc.getRequest().setUri(afterLoginUrl);
        exc.setOriginalRequestUri(afterLoginUrl);
        oauth2.respondWithRedirect(exc);
        Session session = oauth2.getSessionManager().getSession(exc);
        session.put("defaultFlow", defaultFlow);
        session.put("triggerFlow", triggerFlow);
        oauth2.getSessionManager().postProcess(exc); // required to create a session cookie
        values.forEach(header -> exc.getResponse().getHeader().add(header));

        // replace header
        String location = exc.getResponse().getHeader().getFirstValue("Location");
        location = location.replaceAll(defaultFlow, triggerFlow);
        exc.getResponse().getHeader().setValue("Location", location);

        return Outcome.RETURN;
    }


}
