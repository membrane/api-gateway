/* Copyright 2011, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xslt;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.router.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;
import org.xml.sax.*;

import javax.xml.xpath.*;
import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static org.junit.jupiter.api.Assertions.*;

public class XSLTInterceptorTest {

    Exchange exc = new Exchange(null);
    final XPath xpath = XPathFactory.newInstance().newXPath();

    @Test
    void testRequest() throws Exception {
        exc = new Exchange(null);
        exc.setResponse(ok().body(getClass().getResourceAsStream("/customer.xml"), true).build());

        XSLTInterceptor i = new XSLTInterceptor();
        i.setXslt("classpath:/customer2person.xsl");
        i.init(new DummyTestRouter());
        i.handleResponse(exc);

        //printBodyContent();
        assertXPath("/person/name/first", "Rick");
        assertXPath("/person/name/last", "Cort\u00e9s Ribotta");
        assertXPath("/person/address/street",
                "Calle P\u00fablica \"B\" 5240 Casa 121");
        assertXPath("/person/address/city", "Omaha");
    }

    @Test
    void testXSLTParameter() throws Exception {
        exc = new Exchange(null);
        exc.setResponse(ok().body(getClass().getResourceAsStream("/customer.xml"), true).build());

        exc.setProperty("XSLT_COMPANY", "predic8");

        XSLTInterceptor i = new XSLTInterceptor();
        i.setXslt("classpath:/customer2personAddCompany.xsl");
        i.init(new DummyTestRouter());
        i.handleResponse(exc);

        //printBodyContent();
        assertXPath("/person/name/first", "Rick");
        assertXPath("/person/name/last", "Cort\u00e9s Ribotta");
        assertXPath("/person/address/street",
                "Calle P\u00fablica \"B\" 5240 Casa 121");
        assertXPath("/person/address/city", "Omaha");
        assertXPath("/person/company", "predic8");
    }

    @Test
    void noConentInProlog() throws Exception {
        exc = get("http://localhost/").body("rubbish<?xml verion='1.0'?>").buildExchange();

        var i = new XSLTInterceptor();
        i.setXslt("classpath:/customer2personAddCompany.xsl");
        i.init(new DummyTestRouter());
        assertEquals(ABORT, i.handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        String body = exc.getResponse().getBodyAsStringDecoded();
        System.out.println(body);
        assertTrue(body.contains("rubbish"));
        assertTrue(body.contains("not allowed in prolog"));
    }

    @SuppressWarnings("unused")
    private void printBodyContent() throws Exception {
        InputStream i = exc.getResponse().getBodyAsStream();
        int read;
        byte[] buf = new byte[4096];
        while ((read = i.read(buf)) != -1) {
            System.out.write(buf, 0, read);
        }
    }

    private void assertXPath(String xpathExpr, String expected)
            throws XPathExpressionException {
        assertEquals(expected, xpath.evaluate(xpathExpr, new InputSource(exc
                .getResponse().getBodyAsStream())));
    }

}
