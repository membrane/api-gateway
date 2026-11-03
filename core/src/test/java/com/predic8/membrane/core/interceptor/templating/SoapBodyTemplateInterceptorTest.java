/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.http.Request.get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoapBodyTemplateInterceptorTest {


    @ParameterizedTest
    @CsvSource({
            "1.1,1.1",
            "11,1.1",
            "1.2,1.2",
            "12,1.2",
    })
    void setSOAPVersion_validTokens_normalizeToCanonical(String input, String expectedCanonical) {


        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        i.setVersion(input);
        assertEquals(expectedCanonical, i.getVersion());
    }

    @Test
    void setWrongSOAPVersion() {
        assertThrows(ConfigurationException.class, () -> setSoapVersion("1.3"));
    }

    @Test
    void contentType_defaultsToSoap11TextXml() {
        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        assertEquals("1.1", i.getVersion());
        assertEquals("text/xml", i.getContentType());
    }

    @Test
    void contentType_updatesWithVersion() {
        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        i.setVersion("1.2");
        assertEquals("1.2", i.getVersion());
        assertEquals("application/soap+xml", i.getContentType());
    }

    @Test
    void explicitContentType_overridesVersionDefault() {
        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        i.setContentType("text/xml;charset=UTF-8");

        assertEquals("text/xml;charset=UTF-8", i.getContentType());
    }

    @Test
    void explicitContentType_overridesEvenForSoap12() {
        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        i.setVersion("1.2");
        i.setContentType("text/xml;charset=UTF-8");

        assertEquals("text/xml;charset=UTF-8", i.getContentType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1", "1.2"})
    void interpolatedValuesAreXmlEscaped(String version) throws Exception {
        final var i = new SoapBodyTemplateInterceptor();
        i.setVersion(version);
        i.setSrc("<name>${property.city}</name>");
        i.init(new DummyTestRouter());

        final var exc = get("/foo").buildExchange();
        exc.setProperty("city", "</name><evil/><name>");
        i.handleRequest(exc);

        final var body = exc.getRequest().getBodyAsStringDecoded();
        assertTrue(body.contains("<name>&lt;/name&gt;&lt;evil/&gt;&lt;name&gt;</name>"), body);
    }

    @Test
    void prettyFormatsXml() throws Exception {
        final var i = new SoapBodyTemplateInterceptor();
        i.setPretty(true);
        i.setSrc("<a><b>1</b></a>");
        i.init(new DummyTestRouter());

        final var exc = get("/foo").buildExchange();
        i.handleRequest(exc);

        final var body = exc.getRequest().getBodyAsStringDecoded();
        assertFalse(body.contains("<a><b>"), body);
    }

    private static void setSoapVersion(String version) {
        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        i.setVersion(version);
        assertEquals(version, i.getVersion());
    }

}
