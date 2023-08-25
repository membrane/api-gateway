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
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/sampleSoapService/wrongRequest.xml")))).build());
        service.handleRequest(exc);
        assertEquals(SampleSoapService.getSoapFault("city element not found"), exc.getResponse().getBody().toString());
        // System.out.println(exc.getResponse().getBody().toString());
    }

    @Test
    public void validRequest1Test() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/sampleSoapService/request1.xml")))).build());
        service.handleRequest(exc);
        assertEquals(getValidResponse("Germany", "84 million"), exc.getResponse().getBody().toString());
    }

    @Test
    public void validRequest2Test() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/sampleSoapService/request2.xml")))).build());
        service.handleRequest(exc);
        assertEquals(getValidResponse("England", "56 million"), exc.getResponse().getBody().toString());
    }

    @Test
    public void validRequest3Test() throws Exception {
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(IOUtils.toByteArray(Objects.requireNonNull(this.getClass().getResourceAsStream("/sampleSoapService/request3.xml")))).build());
        service.handleRequest(exc);
        assertEquals(getValidResponse("USA", "332 million"), exc.getResponse().getBody().toString());
        //TODO strings are equal but test fails
    }


    private String getValidResponse(String country, String population) {
        return  "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
                "  <s:Body>\n" +
                "    <cs:cityDetails>\n" +
                "      <cs:country>" + country + "</cs:country>\n" +
                "      <cs:population>" + population + "</cs:population>\n" +
                "    </cs:cityDetails>\n" +
                "  </s:Body>\n" +
                "</s:Envelope>\n";
    }
}
