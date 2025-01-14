package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.lang.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.lang.ExchangeExpression.Language.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SetPropertyInterceptorJsonpathTest extends AbstractSetPropertyInterceptorTest {

    @Override
    protected ExchangeExpression.Language getLanguage() {
        return JSONPATH;
    }


    @Test
    void map() {
        interceptor.setFieldName("countries");
        interceptor.setValue("${$.countries}");
        interceptor.init(router);
        interceptor.handleRequest(exc);
        var m = exc.getProperty("countries", Map.class);
        assertEquals("Germany", m.get("de"));
        assertEquals("France", m.get("fr"));
    }
}
