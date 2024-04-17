package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.*;

import java.util.*;



public class OneInterceptorFlowTest extends AbstractInterceptorFlowTest{
    @Override
    protected List<Interceptor> interceptors() {
        return List.of(new FlowTestInterceptor("a"));
    }

    @Override
    protected String flow() {
        return "aA";
    }
}
