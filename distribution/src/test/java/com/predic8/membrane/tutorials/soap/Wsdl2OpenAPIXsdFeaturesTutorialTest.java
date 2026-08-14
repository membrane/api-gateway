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

package com.predic8.membrane.tutorials.soap;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

public class Wsdl2OpenAPIXsdFeaturesTutorialTest extends AbstractSOAPTutorialTest {

    private static final String BASE = "http://localhost:2000/features/";
    private static final String SPEC_PATH = "/api-docs/xsd-features-v1-0-0";

    @Override
    protected String getTutorialYaml() {
        return "97-WSDL-XSD-Features.yaml";
    }

    @Test
    void apiDocsListsTheGeneratedSpec() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/api-docs")
        .then()
            .statusCode(200)
            .body("'xsd-features-v1-0-0'.openapi_link", equalTo(SPEC_PATH));
        // @formatter:on
    }

    /**
     * Step 2 of the tutorial: the constraints that exist only in the generated spec. The spec is
     * served as YAML, so these are text assertions; the precise schema structure behind each
     * keyword is pinned by XsdToSchemaTest.
     */
    @Test
    void specCarriesTheSchemaOnlyConstraints() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000" + SPEC_PATH)
        .then()
            .statusCode(200)
            .body(containsString("minLength"))
            .body(containsString("maxLength"))
            .body(containsString("pattern"))
            .body(containsString("- \"null\""))   // nillable, as OpenAPI 3.1 spells it
            .body(containsString("$value"))
            .body(containsString("exclusiveMinimum"))
            .body(containsString("exclusiveMaximum"))
            .body(containsString("ACTIVE"))       // xsd:enumeration
            .body(containsString("EUR"));         // default=
        // @formatter:on
    }

    @Test
    void primitiveTypesKeepTheirJsonType() {
        callFeature("get-primitive-types", "{\"id\":1}")
            .body("count", equalTo(42))
            .body("price", equalTo(9.99f))
            .body("active", equalTo(true))
            .body("text", equalTo("Widget"));
    }

    @Test
    void singleOccurrenceIsStillAnArray() {
        callFeature("get-array", "{\"id\":1}")
            .body("tag", hasSize(1))
            .body("tag", contains("java"));
    }

    @Test
    void nilElementBecomesJsonNull() {
        callFeature("get-nillable", "{\"note\":null}")
            .body("label", equalTo("open account"))
            .body("closedAt", nullValue());
    }

    @Test
    void simpleContentSplitsIntoValueAndAttribute() {
        callFeature("get-simple-content", "{\"id\":1}")
            .body("price.'@currency'", equalTo("EUR"))
            .body("price.'$value'", equalTo(9.99f));
    }

    @Test
    void attributesBecomeAtPrefixedProperties() {
        callFeature("get-attributes", "{\"id\":1}")
            .body("record.'@id'", equalTo("123"))
            .body("record.'@kind'", equalTo("city"))
            .body("record.name", equalTo("Berlin"));
    }

    @Test
    void extensionInheritsTheBaseTypesFields() {
        callFeature("get-extension", "{\"id\":1}")
            .body("order.orderId", equalTo("A-1"))
            .body("order.customer", equalTo("Alice"))
            .body("order.priority", equalTo("high"));
    }

    @Test
    void restrictionDropsTheFieldItLeavesOut() {
        callFeature("get-restriction", "{\"id\":1}")
            .body("order.orderId", equalTo("A-1"))
            .body("order.customer", nullValue());
    }

    @Test
    void groupFieldsAppearInline() {
        callFeature("get-group-ref", "{\"id\":1}")
            .body("status", equalTo("ok"))
            .body("createdBy", equalTo("alice"));
    }

    @Test
    void typeFromTheImportedNamespaceIsResolved() {
        callFeature("get-imported-type", "{\"id\":1}")
            .body("address.street", equalTo("Main St 1"))
            .body("address.city", equalTo("Berlin"));
    }

    @Test
    void eitherChoiceAlternativeAloneIsAccepted() {
        callFeature("get-choice", "{\"byId\":7}").body("status", equalTo("ok"));
        callFeature("get-choice", "{\"byName\":\"Berlin\"}").body("status", equalTo("ok"));
    }

    @Test
    void jsonKeyOrderDoesNotMatterForASequence() {
        callFeature("get-sequence", "{\"third\":\"c\",\"first\":\"a\",\"second\":\"b\"}")
            .body("status", equalTo("ordered"));
    }

    private static io.restassured.response.ValidatableResponse callFeature(String path, String body) {
        // @formatter:off
        return given()
            .contentType(JSON)
            .body(body)
        .when()
            .post(BASE + path)
        .then()
            .statusCode(200)
            .contentType(JSON);
        // @formatter:on
    }
}
