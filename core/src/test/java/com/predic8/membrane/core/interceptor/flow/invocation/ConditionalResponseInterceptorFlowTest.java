package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;

import java.util.*;

public class ConditionalResponseInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {


        ConditionalInterceptor ci = new ConditionalInterceptor();
        ci.setTest("true");
        ci.getInterceptors().add(new FlowTestInterceptor("b"));

        ResponseInterceptor rsi = new ResponseInterceptor();
        rsi.getInterceptors().add(ci);

        return List.of(new FlowTestInterceptor("a"),
                rsi,
                new FlowTestInterceptor("c"));
    }

    @Override
    protected String flow() {
        return ">a>c<c<b<a";
    }
}
