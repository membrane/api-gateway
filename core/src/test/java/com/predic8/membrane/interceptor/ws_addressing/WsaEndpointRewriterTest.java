package com.predic8.membrane.interceptor.ws_addressing;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.ws_addressing.DecoupledEndpointRegistry;
import com.predic8.membrane.core.interceptor.ws_addressing.WsaEndpointRewriter;
import org.junit.Before;
import org.junit.Test;

import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

import static org.junit.Assert.assertTrue;

public class WsaEndpointRewriterTest {
    private InputStream input;

    @Before
    public void setUp() {
        input = WsaEndpointRewriterTest.class.getResourceAsStream("/interceptor/ws_addressing/body.xml");
    }

    @Test
    public void testRewriteEndpointAddress() throws XMLStreamException {
        System.out.println(input);

        StringWriter writer = new StringWriter();
        WsaEndpointRewriter rewriter = new WsaEndpointRewriter(new DecoupledEndpointRegistry());

        int port = 2000;
        rewriter.rewriteEndpoint(new InputStreamReader(input), writer, port, new Exchange(null));

        String rewrittenXml = writer.toString();

        System.out.println(rewrittenXml);

        assertTrue(rewrittenXml.contains("<Address>http://localhost:" + port + "/decoupled_endpoint</Address>"));
    }
}
