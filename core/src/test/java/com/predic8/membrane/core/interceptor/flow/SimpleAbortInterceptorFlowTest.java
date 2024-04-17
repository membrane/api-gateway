package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import java.util.List;

public class SimpleAbortInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        GroovyInterceptor gi = new GroovyInterceptor();
        gi.setSrc("ABORT");

        return List.of(new FlowTestInterceptor("a"),
                gi,
                new FlowTestInterceptor("b"));
    }

    @Override
    protected String flow() {
        return "Internal Server Error";
    }
}
