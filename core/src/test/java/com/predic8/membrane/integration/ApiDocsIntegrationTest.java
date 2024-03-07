package com.predic8.membrane.integration;

import com.predic8.membrane.core.Router;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiDocsIntegrationTest {
    private static Router router;

    @BeforeAll
    public static void setUp() throws Exception {
        router = Router.init("classpath:/openapi/docs/proxies.xml");
    }

    @Test
    public void test() throws IOException {
        GetMethod getReq = new GetMethod("http://localhost:2000");
        new HttpClient().executeMethod(getReq);
        assertEquals(getReq.getResponseContentLength(), "");
    }
}