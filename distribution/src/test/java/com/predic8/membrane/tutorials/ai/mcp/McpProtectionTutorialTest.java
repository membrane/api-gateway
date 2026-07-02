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

package com.predic8.membrane.tutorials.ai.mcp;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

public class McpProtectionTutorialTest extends AbstractMcpTutorialTest {

    private String sessionId;

    @Override
    protected String getTutorialYaml() {
        return "20-MCP-Protection.yaml";
    }

    @BeforeEach
    void initializeMcpSession() {
        // @formatter:off
        Response initialize = given()
                .contentType(JSON)
                .body("""
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "initialize",
                      "params": {
                        "protocolVersion": "2025-03-26",
                        "capabilities": {},
                        "clientInfo": {
                          "name": "tutorial-test",
                          "version": "1.0"
                        }
                      }
                    }
                    """)
            .when()
                .post("http://localhost:2000")
            .then()
                .statusCode(200)
                .contentType(JSON)
                .header("Mcp-Session-Id", notNullValue())
                .extract()
                .response();

        sessionId = initialize.getHeader("Mcp-Session-Id");

        given()
                .contentType(JSON)
                .header("Mcp-Session-Id", sessionId)
                .body("""
                    {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                    """)
            .when()
                .post("http://localhost:2000")
            .then()
                .statusCode(202);
        // @formatter:on
    }

    @Test
    void filtersDeniedToolsFromToolsList() {
        // @formatter:off
        given()
            .contentType(JSON)
            .header("Mcp-Session-Id", sessionId)
            .body("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo(2))
            .body("result.tools.name", contains("listProxies", "getStatistics"))
            .body("result.tools.name", not(hasItem("getExchanges")));
        // @formatter:on
    }

    @Test
    void allowsConfiguredTool() {
        // @formatter:off
        given()
            .contentType(JSON)
            .header("Mcp-Session-Id", sessionId)
            .body("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"listProxies","arguments":{}}}
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200)
            .contentType(JSON)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo(3))
            .body("result.content[0].type", equalTo("text"))
            .body("result.content[0].text", containsString("Protected-MCP-Endpoint"));
        // @formatter:on
    }

    @Test
    void rejectsBlockedTool() {
        // @formatter:off
        given()
            .contentType(JSON)
            .header("Mcp-Session-Id", sessionId)
            .body("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"getExchanges","arguments":{}}}
                """)
        .when()
            .post("http://localhost:2000")
        .then()
            .statusCode(403)
            .contentType(JSON)
            .body("jsonrpc", equalTo("2.0"))
            .body("id", equalTo(4))
            .body("error.code", equalTo(-32602))
            .body("error.message", containsString("getExchanges"));
        // @formatter:on
    }
}
