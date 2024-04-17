package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.*;

import java.util.*;


public class RequestInterceptorFlowTest extends AbstractInterceptorFlowTest{
    @Override
    protected List<Interceptor> interceptors() {

        RequestInterceptor ri = new RequestInterceptor();
        ri.getInterceptors().add(new FlowTestInterceptor("b"));

        return List.of(new FlowTestInterceptor("a"),
                ri,
                new FlowTestInterceptor("c"));
    }

    @Override
    protected String flow() {
        return "abcCA";
    }
}
