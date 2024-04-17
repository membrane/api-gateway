package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;

import java.util.*;

public class FalseConditionalInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ConditionalInterceptor ri = new ConditionalInterceptor();
        ri.setTest("false");
        ri.getInterceptors().add(new FlowTestInterceptor("b"));

        return List.of(new FlowTestInterceptor("a"),
                ri,
                new FlowTestInterceptor("c"));
    }

    @Override
    protected String flow() {
        return ">a>c<c<a";
    }
}
