package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

public class FlowTestInterceptor extends AbstractInterceptor {

    private final String name;

    public FlowTestInterceptor(String name) {
        this.name = name;
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        addStringToBody(exc.getRequest(),">" + name);
        return CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        addStringToBody(exc.getResponse(),"<" + name);
        return CONTINUE;
    }

    @Override
    public void handleAbort(Exchange exc) {
        Response msg;
        if (exc.getResponse() != null) {
            msg = exc.getResponse();
        } else  {
            msg = Response.ok().body(exc.getRequest().getBodyAsStringDecoded()).build();
            exc.setResponse(msg);
        }
        addStringToBody(msg,"?" + name);
        exc.setProperty("status", "aborted");
    }

    private void addStringToBody(Message msg, String s) {
        msg.setBodyContent((msg.getBodyAsStringDecoded() + s).getBytes());
    }
}
