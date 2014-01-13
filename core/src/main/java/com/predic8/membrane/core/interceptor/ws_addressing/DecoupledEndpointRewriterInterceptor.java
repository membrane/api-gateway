package com.predic8.membrane.core.interceptor.ws_addressing;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

import java.io.StringWriter;
import java.io.Writer;

public class DecoupledEndpointRewriterInterceptor extends AbstractInterceptor {
    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        System.out.println("DecoupledEndpointRewriterInterceptor.handleRequest()");
        System.out.println(getRegistry());

        Writer writer = new StringWriter();

        System.out.println("Body: " + exc.getRequest().getBodyAsStringDecoded());

        new DecoupledEndpointRewriter(getRegistry()).rewriteToElement(exc.getRequest().getBodyAsStream(), writer, exc);
        System.out.println(writer.toString());

        exc.getRequest().setBodyContent(writer.toString().getBytes());

        return Outcome.CONTINUE;
    }

    private DecoupledEndpointRegistry getRegistry() {
        return getRouter().getBeanFactory().getBean(DecoupledEndpointRegistry.class);
    }
}