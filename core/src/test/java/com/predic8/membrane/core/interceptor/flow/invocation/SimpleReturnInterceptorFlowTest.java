package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;

import java.util.List;

public class SimpleReturnInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ReturnInterceptor ri = new ReturnInterceptor();

        return List.of(new FlowTestInterceptor("a"),
                ri,
                new FlowTestInterceptor("b"));
    }

    @Override
    protected String flow() {
        return ">a<a";
    }
}
