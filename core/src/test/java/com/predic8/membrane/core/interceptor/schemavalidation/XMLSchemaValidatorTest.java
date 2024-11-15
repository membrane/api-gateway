package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import org.apache.commons.lang3.time.*;
import org.junit.jupiter.api.*;
import org.slf4j.*;

import javax.xml.transform.dom.*;
import java.util.*;

class XMLSchemaValidatorTest {

    private static final Logger log = LoggerFactory.getLogger(XMLSchemaValidatorTest.class.getName());

    XMLSchemaValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        validator = new XMLSchemaValidator(new ResolverMap(), "src/test/resources/validation/order.xsd", (message, exc) -> log.info("Validation failure: " + message));
    }

    @Test
    void validate() throws Exception {

        Exchange exc = Request.post("/foo").body(this.getClass().getResourceAsStream("/validation/order.xml").readAllBytes()).buildExchange();

        DOMSource source = new DOMSource();

        long times = 1_000_000;

        Timer t1 = new Timer();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for (int i = 0; i < times; i++) {
            Outcome res = validator.validateMessage(exc, exc.getRequest());
            //System.out.println("res = " + res);
        }

        System.out.println("%d time = %s ".formatted(times, stopWatch));

    }

    @Test
    void validateSingle() throws Exception {

        Exchange exc = Request.post("/foo").body(this.getClass().getResourceAsStream("/validation/order.xml").readAllBytes()).buildExchange();

        Outcome res = validator.validateMessage(exc, exc.getRequest());

        // @TODO
        System.out.println("exc.getRequest().getBody() = " + exc.getRequest().getBody());


    }
}