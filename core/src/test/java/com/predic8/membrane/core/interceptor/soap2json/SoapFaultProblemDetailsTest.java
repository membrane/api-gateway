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
package com.predic8.membrane.core.interceptor.soap2json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SoapFaultProblemDetailsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final SoapFaultException FAULT = new SoapFaultException(
            "soap:Server", "NullPointerException at internal-host-7", Map.of("outOfStock", Map.of("available", 3)));

    private static final Schema<?> OUT_OF_STOCK_DECLARED = new ObjectSchema().addProperty("outOfStock", new ObjectSchema());

    @Test
    void aFaultIsA500CarryingTheDeclaredDetails() throws Exception {
        final var response = SoapFaultProblemDetails.of(FAULT, OUT_OF_STOCK_DECLARED, true, "soap 2 json");

        assertEquals(500, response.getStatusCode());
        final var json = MAPPER.readTree(response.getBodyAsStringDecoded());
        assertEquals(3, json.get("details").get("outOfStock").get("available").asInt());
    }

    @Test
    void faultCodeAndMessageStayOutOfAProductionResponse() {
        final var body = SoapFaultProblemDetails.of(FAULT, OUT_OF_STOCK_DECLARED, true, "soap 2 json").getBodyAsStringDecoded();

        assertFalse(body.contains("internal-host-7"), body);
        assertFalse(body.contains("soap:Server"), body);
    }

    @Test
    void detailTheWsdlDoesNotDeclareStaysInternal() throws Exception {
        final var fault = new SoapFaultException("soap:Server", "boom",
                Map.of("outOfStock", Map.of("available", 3), "trace", "at internal-host-7"));

        final var production = SoapFaultProblemDetails.of(fault, OUT_OF_STOCK_DECLARED, true, "soap 2 json").getBodyAsStringDecoded();
        assertEquals(3, MAPPER.readTree(production).get("details").get("outOfStock").get("available").asInt(), production);
        assertFalse(production.contains("internal-host-7"), production);

        final var development = SoapFaultProblemDetails.of(fault, OUT_OF_STOCK_DECLARED, false, "soap 2 json").getBodyAsStringDecoded();
        assertTrue(development.contains("internal-host-7"), "development mode keeps it as an internal aid: " + development);
    }

    @Test
    void withoutADeclaredFaultNoDetailIsPublished() throws Exception {
        final var response = SoapFaultProblemDetails.of(FAULT, null, true, "soap 2 json");

        assertNull(MAPPER.readTree(response.getBodyAsStringDecoded()).get("details"));
    }

    @Test
    void aFaultWithoutDetailHasNoDetailsField() throws Exception {
        final var response = SoapFaultProblemDetails.of(new SoapFaultException("soap:Server", "boom", null), OUT_OF_STOCK_DECLARED, false, "soap 2 json");

        assertNull(MAPPER.readTree(response.getBodyAsStringDecoded()).get("details"));
    }
}
