package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

class ConditionalInterceptorGroovyTest {

    @Test
    void simpleGroovy() {
    }
}

class MockInterceptor extends AbstractInterceptor {

    boolean handleRequestCalled;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        handleRequestCalled = true;
        return CONTINUE;
    }
}