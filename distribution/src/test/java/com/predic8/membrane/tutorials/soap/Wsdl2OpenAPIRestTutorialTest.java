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

public class Wsdl2OpenAPIRestTutorialTest extends AbstractSOAPTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "96-WSDL-to-OpenAPI-REST.yaml";
    }

    @Test
    void getPartnersReturnsThreePartners() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/partners")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("partners", hasSize(3))
            .body("partners[0].name", equalTo("Alice"))
            .body("partners[1].name", equalTo("Bob"))
            .body("partners[2].name", equalTo("Carol"));
        // @formatter:on
    }

    /**
     * Step 3 of the tutorial: getPartners is mapped to GET, so its city field is published as a
     * query parameter. One partner comes back only if the value reached the SOAP backend.
     */
    @Test
    void queryParameterReachesTheSoapBackend() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/partners?city=Berlin")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("partners", hasSize(1))
            .body("partners[0].name", equalTo("Alice"));
        // @formatter:on
    }

    @Test
    void undeclaredQueryParameterIsNotPassedOn() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/partners?nonsense=Berlin")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("partners", hasSize(3));
        // @formatter:on
    }

    @Test
    void specDeclaresTheQueryParametersWithTheirXsdConstraints() {
        String specPath = given().when().get("http://localhost:2000/api-docs")
                .jsonPath().getString("values().openapi_link[0]");

        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000" + specPath)
        .then()
            .statusCode(200)
            .body(containsString("in: \"query\""))
            .body(containsString("PUBLIC_AUTHORITY"));   // the PartnerKind enum survives the mapping
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
    void updatePartnerReturnsUpdatedPartner() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"name\":\"Alice Updated\",\"birthDate\":\"1985-03-15\",\"kind\":\"PERSON\",\"address\":{\"street\":\"Main St\",\"houseNumber\":\"1\",\"postalCode\":\"12345\",\"city\":\"Springfield\",\"country\":\"US\"}}")
        .when()
            .put("http://localhost:2000/partners/1")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("partner.name", equalTo("Alice Updated"))
            .body("partner.kind", equalTo("PERSON"));
        // @formatter:on
    }

    @Test
    void deletePartnerReturnsSuccess() {
        // @formatter:off
        given()
        .when()
            .delete("http://localhost:2000/partners/1")
        .then()
            .statusCode(200)
            .contentType(JSON);
        // @formatter:on
    }

    @Test
    void unmappedMethodOnMappedPathReturns405() {
        // /partners/{id} is mapped for GET, PUT and DELETE but not POST. Without a 405 the
        // untransformed JSON body would be forwarded to the SOAP backend.
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"name\":\"Dave\"}")
        .when()
            .post("http://localhost:2000/partners/1")
        .then()
            .statusCode(405)
            .header("Allow", equalTo("GET, PUT, DELETE"));
        // @formatter:on
    }

    @Test
    void createPartnerReturnsEmptyObject() {
        // @formatter:off
        given()
            .contentType(JSON)
            .body("{\"name\":\"Dave\",\"birthDate\":\"1990-01-01\",\"kind\":\"PERSON\",\"address\":{\"street\":\"Oak Ave\",\"houseNumber\":\"5\",\"postalCode\":\"54321\",\"city\":\"Portland\",\"country\":\"US\"}}")
        .when()
            .post("http://localhost:2000/partners")
        .then()
            .statusCode(200)
            .contentType(JSON);
        // @formatter:on
    }
}
