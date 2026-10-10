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

package com.predic8.membrane.core.interceptor.flow.choice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DummyTestRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import com.predic8.membrane.core.util.xml.parser.XmlParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.flow.choice.ChooseInterceptor.validateChoices;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.XPATH;
import static org.junit.jupiter.api.Assertions.*;

class ChooseInterceptorTest {

    // Regression for https://github.com/membrane/api-gateway/issues/3385
    @ParameterizedTest(name = "expression failure with otherwise={0}")
    @ValueSource(booleans = {false, true})
    void expressionFailureAbortsWithoutRunningOtherwise(boolean withOtherwise) throws Exception {
        var failingCase = new Case();
        // Valid SpEL syntax that fails when evaluated on the exchange.
        failingCase.setTest("request.nonexistentProperty == true");
        failingCase.setFlow(List.of());

        var otherwiseInvoked = new AtomicBoolean();
        var otherwise = new Otherwise();
        otherwise.setFlow(List.of(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                otherwiseInvoked.set(true);
                return CONTINUE;
            }
        }));

        var choose = new ChooseInterceptor();
        choose.setChoices(withOtherwise ? List.of(failingCase, otherwise) : List.of(failingCase));
        choose.init(new DummyTestRouter());

        var exc = new Exchange(null);
        exc.setRequest(Request.get("/").build());
        var outcome = choose.handleRequest(exc);
        var problem = new ObjectMapper().readTree(exc.getResponse().getBodyAsStringDecoded());

        assertAll(
            () -> assertEquals(ABORT, outcome, "An evaluation error must stop the request flow"),
            () -> assertFalse(otherwiseInvoked.get(), "An evaluation error must not run otherwise"),
            () -> assertEquals(500, exc.getResponse().getStatusCode()),
            () -> assertEquals("https://membrane-api.io/problems/internal/choose/expression-evaluation",
                problem.path("see").asText()),
            () -> assertEquals("Error evaluating expression on exchange in choose plugin.",
                problem.path("title").asText())
        );
    }

    @Test
    void bodyErrorKeepsDetailAndIs400() throws Exception {
        var xpathCase = new Case();
        xpathCase.setLanguage(XPATH);
        xpathCase.setTest("/a");
        xpathCase.setFlow(List.of());

        var choose = new ChooseInterceptor();
        choose.setChoices(List.of(xpathCase));
        choose.init(new DummyTestRouter());

        var exc = Request.post("/").contentType(APPLICATION_XML).body("<a><b></a>").buildExchange();
        assertEquals(ABORT, choose.handleRequest(exc));
        var problem = HardenedXmlParser.getInstance().parse(new InputSource(exc.getResponse().getBodyAsStreamDecoded())); // XML, like the request

        assertAll(
            () -> assertEquals(400, exc.getResponse().getStatusCode()),
            () -> assertEquals(parseError("<a><b></a>"), problem.getElementsByTagName("detail").item(0).getTextContent())
        );
    }

    @Test
    void validateChoices_acceptsValidAndRejectsInvalid() {
        assertDoesNotThrow(() -> validateChoices(List.of(new Case(), new Case())));
        assertDoesNotThrow(() -> validateChoices(List.of(new Case(), new Otherwise())));

        assertThrows(ConfigurationException.class, () -> validateChoices(List.of(new Otherwise(), new Case())));
        assertThrows(ConfigurationException.class, () -> validateChoices(List.of(new Case(), new Otherwise(), new Otherwise())));
        assertThrows(ConfigurationException.class, () -> validateChoices(List.of(new Case(), new Otherwise(), new Case())));
    }

    /**
     * @return the message the XML parser fails with on <code>xml</code>, which is localized
     */
    private static String parseError(String xml) {
        return assertThrows(XmlParseException.class,
            () -> HardenedXmlParser.getInstance().parse(new InputSource(new StringReader(xml)))).getMessage();
    }
}
