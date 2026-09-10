/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import javax.xml.transform.TransformerFactory;

import static com.predic8.membrane.core.http.Header.VALIDATION_ERROR_SOURCE;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematronValidatorTest {

    private static final String SCHEMATRON = "src/test/resources/validation/order.sch";

    private static final String ORDER_WITHOUT_ITEM = """
            <order />
            """;

    private static final String ORDER_WITH_ITEM = """
            <order><item /></order>
            """;

    @Test
    void valid() throws Exception {
        Exchange exc = post("/foo").body(ORDER_WITH_ITEM).buildExchange();
        assertEquals(CONTINUE, createValidator(ErrorDetailsPolicy.FULL).validateMessage(exc, REQUEST));
    }

    /**
     * A non-null {@link ValidatorInterceptor.FailureHandler} used to suppress the report, which
     * made {@code <validator schematron="...">} always answer with an empty {@code <error/>}:
     * {@code createFailureHandler()} never returns null, not even for {@code failureHandler="response"}.
     */
    @Test
    void failureHandlerDoesNotSuppressTheReport() throws Exception {
        Exchange exc = post("/foo").body(ORDER_WITHOUT_ITEM).buildExchange();
        var validator = createValidator(ErrorDetailsPolicy.FULL);

        assertEquals(ABORT, validator.validateMessage(exc, REQUEST));

        String body = exc.getResponse().getBodyAsStringDecoded();
        assertEquals(400, exc.getResponse().getStatusCode());
        assertEquals(REQUEST.name().toLowerCase(), exc.getResponse().getHeader().getFirstValue(VALIDATION_ERROR_SOURCE));
        assertTrue(body.contains("failed-assert"), body);
        assertTrue(body.contains("An order must contain at least one item."), body);
        assertEquals(1, validator.getInvalid());
    }

    @Test
    void validationDetailsOffOmitsTheReport() throws Exception {
        Exchange exc = post("/foo").body(ORDER_WITHOUT_ITEM).buildExchange();

        assertEquals(ABORT, createValidator(new ErrorDetailsPolicy(false, false)).validateMessage(exc, REQUEST));

        String body = exc.getResponse().getBodyAsStringDecoded();
        assertEquals(400, exc.getResponse().getStatusCode());
        assertFalse(body.contains("failed-assert"), body);
        assertFalse(body.contains("An order must contain at least one item."), body);
        assertTrue(body.contains("<error></error>"), body);
    }

    @Test
    void productionModeKeepsTheReport() throws Exception {
        Exchange exc = post("/foo").body(ORDER_WITHOUT_ITEM).buildExchange();

        assertEquals(ABORT, createValidator(new ErrorDetailsPolicy(true, true)).validateMessage(exc, REQUEST));

        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("An order must contain at least one item."),
                "the schematron is public, so its errors stay visible in production");
    }

    private static SchematronValidator createValidator(ErrorDetailsPolicy policy) throws Exception {
        return new SchematronValidator(SCHEMATRON, ValidatorInterceptor.FailureHandler.VOID,
                new TestRouter(), transformerFactoryBean(), policy);
    }

    private static BeanFactory transformerFactoryBean() {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("transformerFactory", TransformerFactory.newInstance());
        return beanFactory;
    }
}
