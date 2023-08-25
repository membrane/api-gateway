package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleSoapInterceptorTest {

    private static SampleSoapService service;
    private static Exchange exc = new Exchange(null);
    @BeforeAll
    public static void setUp() throws IOException {
        service = new SampleSoapService();
    }

    @Test
    public void notFoundTest() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/sampleSoapService/request1.xml")))).build());
        service.handleRequest(exc);
        assertEquals(SampleSoapService.getSoapFault("city element not found"), exc.getResponse().getBody().toString());
        // System.out.println(exc.getResponse().getBody().toString());
    }
}
