package com.predic8.membrane.core.interceptor.flow.invocation;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;

import java.util.List;

public class RequestReturnInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        RequestInterceptor rqi = new RequestInterceptor();
        ReturnInterceptor ri = new ReturnInterceptor();
        rqi.getInterceptors().add(ri);

        return List.of(new FlowTestInterceptor("a"),
                rqi,
                new FlowTestInterceptor("b"));
    }

    @Override
    protected String flow() {
        return ">a<a";
    }
}