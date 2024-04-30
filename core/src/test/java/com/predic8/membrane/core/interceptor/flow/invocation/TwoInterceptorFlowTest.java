package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.*;

import java.util.*;


public class TwoInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {
        return List.of(new FlowTestInterceptor("a"), new FlowTestInterceptor("b"));
    }

    @Override
    protected String flow() {
        return ">a>b<b<a";
    }
}
