package com.predic8.membrane.core.interceptor.soap;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
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

    private void testValidRequest(String requestFileName, String country, String population) throws Exception {
        InputStream requestStream = getClass().getResourceAsStream("/sampleSoapService/" + requestFileName);
        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML).body(IOUtils.toByteArray(Objects.requireNonNull(requestStream))).build());
        service.handleRequest(exc);
        assertEquals(getValidResponse(country, population), exc.getResponse().getBody().toString());
    }

    @Test
    public void validRequest1Test() throws Exception {
        testValidRequest("request1.xml", "Germany", "84 million");
    }

    @Test
    public void validRequest2Test() throws Exception {
        testValidRequest("request2.xml", "England", "56 million");
    }

    @Test
    public void validRequest3Test() throws Exception {
        testValidRequest("request3.xml", "USA", "332 million");
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
