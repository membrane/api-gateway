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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class Wsdl2OpenAPIOperationsTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "96-WSDL-to-OpenAPI-Operations.yaml";
    }

    @Test
    void getPartnersReturnsThreePartners() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/get-partners")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("partners.partner", hasSize(3))
            .body("partners.partner[0].name", equalTo("Alice"))
            .body("partners.partner[1].name", equalTo("Bob"))
            .body("partners.partner[2].name", equalTo("Carol"));
        // @formatter:on
    }

    @Test
    void getPartnerReturnsOnePartner() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/partners/1")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("partner.name", equalTo("Alice"))
            .body("partner.phoneNumber", equalTo("+4930123456"))
            .body("partner.kind", equalTo("PERSON"));
        // @formatter:on
    }

    @Test
    void createPartnerReturnsEmptyObject() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"name\":\"Dave\",\"birthDate\":\"1990-01-01\",\"kind\":\"PERSON\",\"address\":{\"street\":\"Oak Ave\",\"houseNumber\":\"5\",\"postalCode\":\"54321\",\"city\":\"Portland\",\"country\":\"US\"}}")
        .when()
            .post("http://localhost:2000/create-partner")
        .then()
            .statusCode(200)
            .contentType(JSON);
        // @formatter:on
    }
}
