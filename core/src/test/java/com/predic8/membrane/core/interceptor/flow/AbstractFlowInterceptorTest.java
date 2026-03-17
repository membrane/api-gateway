package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import groovy.util.logging.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class AbstractFlowInterceptorTest {

    @Test
    void propagationOfProxy() {
        var r = new TestRouter();
        var p = new ServiceProxy();
        var ri = new RequestInterceptor();
        var ti = new LogInterceptor();
        ri.interceptors.add(ti);
        ri.init(r,p);
        assertSame(r,ti.getRouter());
        assertSame(p,ti.getProxy());
    }
}