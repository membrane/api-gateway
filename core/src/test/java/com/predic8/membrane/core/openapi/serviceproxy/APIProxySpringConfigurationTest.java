package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.apikey.*;
import com.predic8.membrane.core.interceptor.log.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class APIProxySpringConfigurationTest extends AbstractProxySpringConfigurationTest {

    static String publisherSeparate = """
            <api port="2000" name="bool-api">
                <openapi location="src/test/resources/openapi/specs/boolean.yml" validateSecurity="yes"/>
                <apiKey required = "false">
                    <headerExtractor name="X-Api-Key"/>
                </apiKey>
                <headerFilter>
                    <exclude>Origin</exclude>
                </headerFilter>
                <openapiPublisher/>
                <log/>
                <target url="https://api.predic8.de"/>
            </api>""";

    static String noPublisherNoOpenAPIInterceptor = """
            <api port="2000" name="bool-api">
                <openapi location="src/test/resources/openapi/specs/boolean.yml" validateSecurity="yes"/>
                <apiKey required = "false">
                    <headerExtractor name="X-Api-Key"/>
                </apiKey>
                <headerFilter>
                    <exclude>Origin</exclude>
                </headerFilter>
                <log/>
                <target url="https://api.predic8.de"/>
            </api>""";

    @Test
    void interceptorSequenceFromSpringConfiguration() {
        Router router = startSpringContextAndReturnRouter(publisherSeparate);
        APIProxy ap = getApiProxy(router);
        assertEquals(4, ap.getInterceptors().size());
        assertInstanceOf(ApiKeysInterceptor.class, ap.getInterceptors().get(0));
        assertInstanceOf(HeaderFilterInterceptor.class, ap.getInterceptors().get(1));
        assertInstanceOf(OpenAPIPublisherInterceptor.class, ap.getInterceptors().get(2));
        assertInstanceOf(LogInterceptor.class, ap.getInterceptors().get(3));
    }

    @Test
    void interceptorSequenceAferInit() {
        Router router = startSpringContextAndReturnRouter(publisherSeparate);
        APIProxy ap = getApiProxy(router);
        ap.init(router);
        assertEquals(5, ap.getInterceptors().size());
        assertInstanceOf(OpenAPIInterceptor.class, ap.getInterceptors().get(0)); // Got added
        assertInstanceOf(ApiKeysInterceptor.class, ap.getInterceptors().get(1));
        assertInstanceOf(HeaderFilterInterceptor.class, ap.getInterceptors().get(2));
        assertInstanceOf(OpenAPIPublisherInterceptor.class, ap.getInterceptors().get(3));
        assertInstanceOf(LogInterceptor.class, ap.getInterceptors().get(4));
    }

    @Test
    void noPublisherNoOpenAPIInterceptor() {
        Router router = startSpringContextAndReturnRouter(noPublisherNoOpenAPIInterceptor);
        APIProxy ap = getApiProxy(router);
        ap.init(router);
        assertEquals(5, ap.getInterceptors().size());
        assertInstanceOf(OpenAPIPublisherInterceptor.class, ap.getInterceptors().get(0)); // Was added
        assertInstanceOf(OpenAPIInterceptor.class, ap.getInterceptors().get(1)); //
        assertInstanceOf(ApiKeysInterceptor.class, ap.getInterceptors().get(2));
        assertInstanceOf(HeaderFilterInterceptor.class, ap.getInterceptors().get(3));
        assertInstanceOf(LogInterceptor.class, ap.getInterceptors().get(4));
    }
}