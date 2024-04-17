package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

public class EchoInterceptor extends AbstractInterceptor {

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        exc.setResponse(Response.ok().body(exc.getRequest().getBody().getContent()).build());
//        exc.getRequest().setBody(new Body("Leer".getBytes()));
        return Outcome.RETURN;
    }
}
