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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static java.net.URLEncoder.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

public class GraphQLProtectionInterceptorTest {


    private static GraphQLProtectionInterceptor i;

    @BeforeAll
    public static void init() throws Exception {
        HttpRouter router = new HttpRouter();
        i = new GraphQLProtectionInterceptor();
        i.init(router);
    }

    @Test
    public void ok_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{a}"}""",
                Outcome.CONTINUE);
    }

    @Test
    public void ok2_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "operationName": null,
                            "variables": null,
                            "extensions": null
                        }""",
                Outcome.CONTINUE);
    }

    @Test
    public void invalidOperationName() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "operationName": 5
                        }""",
                Outcome.RETURN);
    }

    @Test
    public void invalidVariables() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "variables": 5
                        }""",
                Outcome.RETURN);
    }

    @Test
    public void invalidExtensions() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "{a}",
                            "extensions": { "foo": "bar" }
                        }""", // while this is a validly formed extension, the interceptor (by default) does not allow extensions.
                Outcome.RETURN);
    }

    @Test
    public void ok_POST_plain() throws Exception {
        verifyPost("/",
                "application/graphql",
                """
                        {a}""",
                Outcome.CONTINUE);
    }

    @Test
    public void ok_GET() throws Exception {
        verifyGet("/?query=" + encode("{a}", UTF_8),
                Outcome.CONTINUE);
    }


    @Test
    public void bad_mutation_GET() throws Exception {
        verifyGet("/?query=" + encode("mutation { trigger }", UTF_8),
                Outcome.RETURN);
    }

    @Test
    public void invalidContentType() throws Exception {
        verifyPost("/",
                "application/a/b/c",
                """
                        {"query":"{a}"}""",
                Outcome.RETURN);
    }


    @Test
    public void multipleContentTypeHeaders() throws Exception {
        Exchange e = new Request.Builder().post("/")
                .header("Content-Type", APPLICATION_JSON)
                .header("Content-Type", APPLICATION_JSON)
                .body("""
                        {"query":"{a}"}""").buildExchange();

        Outcome outcome = i.handleRequest(e);

        assertEquals(Outcome.RETURN, outcome);
    }

    @Test
    public void operationName_ok_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query a {b} query c {d}",
                            "operationName": "a"
                        }""",
                Outcome.CONTINUE);
    }

    @Test
    public void operationName_notFound_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query a {b} query c {d}",
                            "operationName": "x"
                        }""",
                Outcome.RETURN);
    }

    @Test
    public void operationName_notUnique_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query x {b} query x {d}",
                            "operationName": "x"
                        }""",
                Outcome.RETURN);
    }

    @Test
    public void operationName_empty_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {
                            "query": "query a {b} ",
                            "operationName": ""
                        }""",
                Outcome.CONTINUE);
    }


    @Test
    public void invalidCharset() throws Exception {
        verifyPost("/",
                "application/json;charset=iso-8859-1",
                """
                        {"query":"{a}"}""",
                Outcome.RETURN);
    }

    @Test
    public void noContentType() throws Exception {
        verifyPost("/",
                null,
                """
                        {"query":"{a}"}""",
                Outcome.RETURN);
    }

    @Test
    public void badContentType() throws Exception {
        verifyPost("/",
                "text/plain",
                """
                        {"query":"{a}"}""",
                Outcome.RETURN);
    }

    @Test
    public void getParameterContainsGraphQLData() throws Exception {
        for (String queryParam : new String[] {
                "query=" + encode("{b}", UTF_8),
                "operationName=foo",
                "variables=foo",
                "extensions=foo"
        }) {
            verifyPost("/?" + queryParam,
                    APPLICATION_JSON,
                    """
                            {"query":"{a}"}""",
                    Outcome.RETURN);
        }
    }

    @Test
    public void duplicateKey_POST() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{a}","query":"{b}"}""",
                Outcome.RETURN);
    }

    @Test
    public void duplicateKey_GET() throws Exception {
        verifyGet("/?query=" + encode("{a}", UTF_8) + "&query=" + encode("{b}", UTF_8),
                Outcome.RETURN);
    }

    @Test
    public void nonExistentFragment() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{...a}"}""",
                Outcome.RETURN);
    }

    @Test
    public void ok_fragment() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"query c {...a} fragment a { b }",
                        "operationName": "c"}""",
                Outcome.CONTINUE);
    }

    @Test
    public void cyclicFragmentSpreads() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"query c {...a} fragment a { ...b } fragment b { ...a }",
                        "operationName": "c"}""",
                Outcome.RETURN);
    }


    @Test
    public void nested() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b } }",
                        "operationName": ""}""",
                Outcome.CONTINUE);
    }

    @Test
    public void inlineFragment() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a ... { b } }",
                        "operationName": ""}""",
                Outcome.CONTINUE);
    }

    @Test
    public void depthOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b { c { d { e { f { g } } } } } } }",
                        "operationName": ""}""",
                Outcome.CONTINUE);
    }

    @Test
    public void depthNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b { c { d { e { f { g { h } } } } } } } }",
                        "operationName": ""}""",
                Outcome.RETURN);
    }


    @Test
    public void recursionOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { b { a { d { a } } } } }",
                        "operationName": ""}""",
                Outcome.CONTINUE);
    }

    @Test
    public void recursionNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"{ a { a { a { a { a { a } } } } } }",
                        "operationName": ""}""",
                Outcome.RETURN);
    }

    @Test
    public void mutationsCountNotOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"mutation abc{} mutation abcd{} mutation abcde{} mutation abcdef{} mutation abcdefg{} mutation abcdefgh{}",
                        "operationName": ""}""",
                Outcome.RETURN);
    }

    @Test
    public void mutationsCountOK() throws Exception {
        verifyPost("/",
                APPLICATION_JSON,
                """
                        {"query":"mutation abc{} mutation abcd{} mutation abcde{} mutation abcdef{} mutation abcdefg{}",
                        "operationName": ""}""",
                Outcome.RETURN);
    }

    @Test
    public void invalidMethod() throws Exception {
        Exchange e = new Request.Builder().put("/").contentType(APPLICATION_JSON).body("{}").buildExchange();

        Outcome outcome = i.handleRequest(e);

        assertEquals(Outcome.RETURN, outcome);
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
