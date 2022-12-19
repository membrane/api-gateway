/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.xml;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class XmlPathExtractorInterceptorTest {

    XmlPathExtractorInterceptor xpe;
    Exchange exc;

    @BeforeEach
    public void setUp() throws IOException {
        xpe = new XmlPathExtractorInterceptor();
        exc = new Exchange(null);
        byte[] resAsByteArray = IOUtils.toByteArray(this.getClass().getResourceAsStream("/xml/project.xml"));

        exc.setRequest(new Request.Builder().contentType(MimeType.TEXT_XML)
                .body(resAsByteArray).build());

        exc.setResponse(new Response.ResponseBuilder().contentType(MimeType.TEXT_XML)
                .body(resAsByteArray).build());

    }

    @Test
    public void validTest() throws Exception {
        xpe.getMappings().add(new XmlPathExtractorInterceptor.Property("/project/part[1]/title", "title"));
        xpe.handleRequest(exc);
        assertEquals("Preparation", exc.getProperty("title"));
    }

    @Test
    public void validResponseTest() throws Exception {
        xpe.getMappings().add(new XmlPathExtractorInterceptor.Property("/project/part[1]/title", "title"));
        xpe.handleResponse(exc);

        assertEquals("Preparation", exc.getProperty("title"));

    }


    @Test
    public void nonExistentPathTest() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            //TODO maybe throw exception here null pointer is not that clear
            xpe.getMappings().add(new XmlPathExtractorInterceptor.Property("/project/project[5]/title", "title"));
            xpe.handleRequest(exc);
        });
    }


    @Test
    public void invalidXpathTest() throws Exception {
        assertThrows(RuntimeException.class, () -> {
            xpe.getMappings().add(new XmlPathExtractorInterceptor.Property("/project/project[5]<>/title", "title"));
            xpe.handleRequest(exc);
        });
    }

    @Test
    public void xpathMissingTest() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            XmlPathExtractorInterceptor.Property prop = new XmlPathExtractorInterceptor.Property();
            prop.setName("title");
            xpe.getMappings().add(prop);
            xpe.handleRequest(exc);
        });
    }

    @Test
    public void nameMissingTest() throws Exception {
        assertThrows(RuntimeException.class, () -> {
            XmlPathExtractorInterceptor.Property prop = new XmlPathExtractorInterceptor.Property();
            prop.setXpath("/project/project[5]<>/title");
            xpe.getMappings().add(prop);
            xpe.handleRequest(exc);
        });
    }
    
    
    @Test
    public void listXpathTest() throws ParserConfigurationException, SAXException, XPathExpressionException, IOException {
        xpe.getMappings().add(new XmlPathExtractorInterceptor.Property("/project/part[2]/item", "items"));
        xpe.handleRequest(exc);

        assertEquals("25", ((List)exc.getProperty("items")).get(1));
        assertEquals("value value", ((List)exc.getProperty("items")).get(3));

    }

}