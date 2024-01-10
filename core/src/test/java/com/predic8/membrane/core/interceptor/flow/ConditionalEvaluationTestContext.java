package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request.Builder;
import com.predic8.membrane.core.http.Response.ResponseBuilder;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.GROOVY;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.SPEL;
import static java.util.List.of;

class ConditionalEvaluationTestContext {

    static boolean evalSpEL(String condition, Object builder) throws Exception {
        return performEval(condition, builder, SPEL);
    }

    static boolean evalGroovy(String condition, Object builder) throws Exception {
        return performEval(condition, builder, GROOVY);
    }

    private static boolean performEval(String condition, Object builder, LanguageType lang) throws Exception {
        var exc = new Exchange(null);
        var mockInt = new ConditionalEvaluationTestContext.MockInterceptor();
        var condInt = new ConditionalInterceptor();

        condInt.setLanguage(lang);
        condInt.setInterceptors(of(mockInt));
        condInt.setTest(condition);
        condInt.init(new HttpRouter());

        switch (builder) {
            case Builder b -> {
                exc.setRequest(b.build());
                condInt.handleRequest(exc);
            }
            case ResponseBuilder b ->  {
                exc.setResponse(b.build());
                condInt.handleResponse(exc);
            }
            default -> throw new IllegalStateException("Unexpected value: " + builder);
        }

        return mockInt.isCalled();
    }

    private static class MockInterceptor extends AbstractInterceptor {

        boolean handleRequestCalled;

        @Override
        public Outcome handleRequest(Exchange exc) throws Exception {
            handleRequestCalled = true;
            return CONTINUE;
        }

        public boolean isCalled() {
            return handleRequestCalled;
        }
    }
}
