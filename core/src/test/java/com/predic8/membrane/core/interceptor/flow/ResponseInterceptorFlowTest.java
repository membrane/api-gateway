package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.Interceptor;
import java.util.List;

public class ResponseInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ResponseInterceptor ri = new ResponseInterceptor();
        ri.getInterceptors().add(new FlowTestInterceptor("b"));

        return List.of(new FlowTestInterceptor("a"),
                ri,
                new FlowTestInterceptor("b"));
    }

    @Override
    protected String flow() {
        return ">a>c<c<b<a";
    }
}
