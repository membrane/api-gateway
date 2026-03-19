package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AbstractFlowInterceptorTest {

    @Test
    void propagationOfProxyAndRouterToNestedInterceptors() {
        var r = new TestRouter();
        var p = new ServiceProxy();
        var ri = new RequestInterceptor();
        var ti = new LogInterceptor();
        ri.setFlow(List.of(ti));
        ri.init(r,p);
        assertSame(r,ti.getRouter());
        assertSame(p,ti.getProxy());
    }
}