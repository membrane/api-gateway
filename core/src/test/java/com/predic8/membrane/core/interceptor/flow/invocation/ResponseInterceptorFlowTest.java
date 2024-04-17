package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.flow.*;

import java.util.List;

public class ResponseInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ResponseInterceptor ri = new ResponseInterceptor();
        ri.getInterceptors().add(new FlowTestInterceptor("b"));

        return List.of(new FlowTestInterceptor("a"),
                ri,
                new FlowTestInterceptor("c"));
    }

    @Override
    protected String flow() {
        return ">a>c<c<b<a";
    }
}
