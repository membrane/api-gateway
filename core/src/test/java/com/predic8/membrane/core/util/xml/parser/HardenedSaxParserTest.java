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
package com.predic8.membrane.core.util.xml.parser;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HardenedSaxParserTest {

    @Test
    void parsesValidXml() throws Exception {
        var reader = HardenedSaxParser.getInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(new DefaultHandler());
        reader.parse(new InputSource(new StringReader("<root><child attr='val'/></root>")));
    }

    @Test
    void rejectsDoctype() {
        assertThrows(SAXException.class, () -> {
            var reader = HardenedSaxParser.getInstance().newSAXParser().getXMLReader();
            reader.setContentHandler(new DefaultHandler());
            reader.parse(new InputSource(new StringReader("<!DOCTYPE r [<!ELEMENT r ANY>]><r/>")));
        });
    }

    @Test
    void doesNotFetchExternalDtd() throws Exception {
        var received = new AtomicBoolean(false);
        int port = freePort();
        HttpRouter router = startRecordingServer(port, received);
        try {
            var reader = HardenedSaxParser.getInstance().newSAXParser().getXMLReader();
            reader.setContentHandler(new DefaultHandler());
            assertThrows(SAXException.class, () ->
                reader.parse(new InputSource(new StringReader(
                    "<?xml version='1.0'?><!DOCTYPE r SYSTEM 'http://127.0.0.1:%d/x.dtd'><r/>".formatted(port)
                )))
            );
        } finally {
            router.shutdown();
        }
        assertFalse(received.get(), "SAX parser must not fetch external DTD");
    }

    public static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    public static HttpRouter startRecordingServer(int port, AtomicBoolean received) throws Exception {
        HttpRouter router = new HttpRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port), "", -1);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(com.predic8.membrane.core.exchange.Exchange exc) {
                received.set(true);
                exc.setResponse(ok("").build());
                return RETURN;
            }
        });
        router.getRuleManager().addProxyAndOpenPortIfNew(sp);
        router.init();
        return router;
    }
}
