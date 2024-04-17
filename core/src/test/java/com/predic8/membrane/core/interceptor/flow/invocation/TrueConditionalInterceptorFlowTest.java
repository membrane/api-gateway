package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.flow.*;

import java.util.List;

public class TrueConditionalInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ConditionalInterceptor ri = new ConditionalInterceptor();
        ri.setTest("true");
        ri.getInterceptors().add(new FlowTestInterceptor("b"));

        return List.of(new FlowTestInterceptor("a"),
                ri,
                new FlowTestInterceptor("c"));
    }

    @Override
    protected String flow() {
        return ">a>b>c<c<b<a";
    }
}
