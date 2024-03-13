package com.predic8.membrane.core.interceptor.log;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.util.*;

public class AccessLogInterceptorTest {

    AccessLogInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        interceptor = new AccessLogInterceptor();

        var variables = new ArrayList<AdditionalVariable>();
        AdditionalVariable av1 = new AdditionalVariable();
        av1.setName("foo");
        av1.setExpression("headers.foo");
        AdditionalVariable av2 = new AdditionalVariable();
        av2.setExpression("headers['X-Forwarded-For']");
        av2.setName("Forwarded");
        variables.add(av1);
        variables.add(av2);

        interceptor.setAdditionalPatternList(variables);
        interceptor.init();
    }

    @Test
    void simple() throws Exception {
        interceptor.handleResponse(Request.get("/foo").header("foo","bar").header("X-Forwarded-For","bazf").buildExchange());
    }

}