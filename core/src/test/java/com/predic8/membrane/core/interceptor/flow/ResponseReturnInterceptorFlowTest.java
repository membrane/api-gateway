package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.misc.ReturnInterceptor;

import java.util.List;

public class ResponseReturnInterceptorFlowTest extends AbstractInterceptorFlowTest {
    @Override
    protected List<Interceptor> interceptors() {

        ResponseInterceptor rsi = new ResponseInterceptor();
        ReturnInterceptor ri = new ReturnInterceptor();
        rsi.getInterceptors().add(ri);

        return List.of(new FlowTestInterceptor("a"),
                rsi,
                new FlowTestInterceptor("b"));
    }

    @Override
    protected String flow() {
        return ">a>b<b<a";
    }
}