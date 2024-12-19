/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.graphql;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.graphql.model.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.net.URLEncoder.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class GraphQLProtectionInterceptorTest {

    private static GraphQLProtectionInterceptor i;

    @BeforeAll
    static void init() throws Exception {
        HttpRouter router = new HttpRouter();
        i = new GraphQLProtectionInterceptor();
        i.init(router);
    }

    @Test
    void ok_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{a}"}""",
                CONTINUE);
    }

    @Test
    void ok2_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "operationName": null,
                            "variables": null,
                            "extensions": null
                        }""",
                CONTINUE);
    }

    @Test
    void ok2_POST_missing_fields() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}"
                        }""",
                CONTINUE);
    }

    @Test
    void invalidOperationName() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "operationName": 5
                        }""",
                RETURN);
    }

    @Test
    void invalidVariables() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "variables": 5
                        }""",
                RETURN);
    }

    @Test
    void invalidExtensions() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "extensions": { "foo": "bar" }
                        }""", // while this is a validly formed extension, the interceptor (by default) does not allow extensions.
                RETURN);
    }

    @Test
    void ok_POST_plain() throws Exception {
        verifyPost("/",
                "application/graphql",
                """
                        {a}""",
                CONTINUE);
    }

    @Test
    void ok_GET() throws Exception {
        verifyGet("/?query=" + encode("{a}", UTF_8),
                CONTINUE);
    }


    @Test
    void bad_mutation_GET() throws Exception {
        verifyGet("/?query=" + encode("mutation { trigger }", UTF_8),
                RETURN);
    }

    @Test
    void invalidContentType() throws Exception {
        verifyPost("/",
                "application/a/b/c",
                """
                        {"query":"{a}"}""",
                RETURN);
    }


    @Test
    void multipleContentTypeHeaders() throws Exception {
        Exchange e = new Request.Builder().post("/")
                .header("Content-Type", APPLICATION_JSON)
                .header("Content-Type", APPLICATION_JSON)
                .body("""
                        {"query":"{a}"}""").buildExchange();

        assertEquals(RETURN, i.handleRequest(e));
    }

    @Test
    void operationName_ok_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query a {b} query c {d}",
                            "operationName": "a"
                        }""",
                CONTINUE);
    }

    @Test
    void operationName_notFound_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query a {b} query c {d}",
                            "operationName": "x"
                        }""",
                RETURN);
    }

    @Test
    void operationName_notUnique_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query x {b} query x {d}",
                            "operationName": "x"
                        }""",
                RETURN);
    }

    @Test
    void operationName_empty_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query a {b} ",
                            "operationName": ""
                        }""",
                CONTINUE);
    }


    @Test
    void invalidCharset() throws Exception {
        verifyPost("/",
                "application/json;charset=iso-8859-1",
                """
                        {"query":"{a}"}""",
                RETURN);
    }

    @Test
    void noContentType() throws Exception {
        verifyPost("/",
                null,
                """
                        {"query":"{a}"}""",
                RETURN);
    }

    @Test
    void badContentType() throws Exception {
        verifyPost("/",
                "text/plain",
                """
                        {"query":"{a}"}""",
                RETURN);
    }

    @Test
    void getParameterContainsGraphQLData() throws Exception {
        for (String queryParam : new String[]{
                "query=" + encode("{b}", UTF_8),
                "operationName=foo",
                "variables=foo",
                "extensions=foo"
        }) {
            verifyPost("/?" + queryParam,
                    APPLICATION_JSON,
                    """
                            {"query":"{a}"}""",
                    RETURN);
        }
    }

    @Test
    void duplicateKey_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{a}","query":"{b}"}""",
                RETURN);
    }

    @Test
    void duplicateKey_GET() throws Exception {
        verifyGet("/?query=" + encode("{a}", UTF_8) + "&query=" + encode("{b}", UTF_8),
                RETURN);
    }

    @Test
    void nonExistentFragment() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{...a}"}""",
                RETURN);
    }

    @Test
    void ok_fragment() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"query c {...a} fragment a { b }",
                        "operationName": "c"}""",
                CONTINUE);
    }

    @Test
    void cyclicFragmentSpreads() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"query c {...a} fragment a { ...b } fragment b { ...a }",
                        "operationName": "c"}""",
                RETURN);
    }


    @Test
    void nested() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b } }",
                        "operationName": ""}""",
                CONTINUE);
    }

    @Test
    void inlineFragment() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a ... { b } }",
                        "operationName": ""}""",
                CONTINUE);
    }

    @Test
    void depthOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b { c { d { e { f { g } } } } } } }",
                        "operationName": ""}""",
                CONTINUE);
    }

    @Test
    void depthNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b { c { d { e { f { g { h } } } } } } } }",
                        "operationName": ""}""",
                RETURN);
    }


    @Test
    void recursionOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b { a { d { a } } } } }",
                        "operationName": ""}""",
                CONTINUE);
    }

    @Test
    void recursionNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { a { a { a { a { a } } } } } }",
                        "operationName": ""}""",
                RETURN);
    }

    @Test
    void mutationsCountNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"mutation abc{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcd{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcde{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcdef{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcdefg{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcdefgh{ addProduct(name: \\"Apple\\", price: 1.99) }",
                        "operationName": ""}""",
                RETURN);
    }

    @Test
    void nestedMutationsCountNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query": "mutation { addProduct(name: \\"Apple\\", price: 1.99) { id } addProduct(name: \\"Orange\\", price: 2.99) { id } addProduct(name: \\"Banana\\", price: 3.99) { id } addProduct(name: \\"Grape\\", price: 4.99) { id } addProduct(name: \\"Mango\\", price: 5.99) { id } addProduct(name: \\"Pear\\", price: 6.99) { id }}"}",
                        "operationName": ""}""",
                RETURN);
    }

    @Test
    void mutationsCountOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"mutation abc{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcd{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcde{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcdef{ addProduct(name: \\"Apple\\", price: 1.99) } mutation abcdefg{ addProduct(name: \\"Apple\\", price: 1.99) }",
                        "operationName": ""}""",
                CONTINUE);
    }

    private OperationDefinition opDefOfType(String type) {
        return new OperationDefinition(new OperationType(type), "", new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    @Test
    void invalidMethod() throws Exception {
        Exchange e = new Request.Builder().put("/").contentType(APPLICATION_JSON).body("{}").buildExchange();

        Outcome outcome = i.handleRequest(e);

        assertEquals(RETURN, outcome);
    }

    private void verifyGet(String url, Outcome expectedOutcome) throws Exception {
        Exchange e = new Request.Builder().get(url).buildExchange();

        Outcome outcome = i.handleRequest(e);

        assertEquals(expectedOutcome, outcome);
    }

    private void verifyPost(String url, String contentType, String body, Outcome expectedOutcome) throws Exception {
        Request.Builder builder = new Request.Builder().post(url);
        if (contentType != null)
            builder.header("Content-Type", contentType);
        Exchange e = builder.body(body).buildExchange();

        Outcome outcome = i.handleRequest(e);

        assertEquals(expectedOutcome, outcome);
    }
}
