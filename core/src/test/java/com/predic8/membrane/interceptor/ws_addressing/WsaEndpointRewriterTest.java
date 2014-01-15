/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
