package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

public class FlowTestInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowTestInterceptor.class);

    private String name;

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

    private void addStringToBody(Message msg, String s) {
        msg.setBodyContent((msg.getBodyAsStringDecoded() + s).getBytes());
    }
}
