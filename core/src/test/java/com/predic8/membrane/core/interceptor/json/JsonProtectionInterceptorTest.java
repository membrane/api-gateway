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

package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.junit.jupiter.api.*;

import static com.google.common.base.Strings.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

public class JsonProtectionInterceptorTest {
    static JsonProtectionInterceptor jpiProd;
    static JsonProtectionInterceptor jpiDev;

    private static JsonProtectionInterceptor buildJPI(boolean prod) throws Exception {
        Router router = new Router();
        router.setProduction(prod);
        JsonProtectionInterceptor jpi = new JsonProtectionInterceptor();

        jpi.setMaxTokens(4096);
        jpi.setMaxSize(10240);
        jpi.setMaxDepth(10);
        jpi.setMaxStringLength(20);
        jpi.setMaxKeyLength(10);
        jpi.setMaxObjectSize(10);
        jpi.setMaxArraySize(2048);

        jpi.init(router);
        return jpi;
    }

    @BeforeAll
    public static void init() throws Exception {
        jpiProd = buildJPI(true);
        jpiDev = buildJPI(false);
    }

    @Test
    public void ok() throws Exception {
        send("""
                {}""",
                CONTINUE);
    }

    @Test
    public void ok2() throws Exception {
        send("""
                {"a":"b"}""",
                CONTINUE);
    }

    @Test
    void duplicateKey() throws Exception {
        send("""
                {"a":1,"a":2}""",
                RETURN,
                1,
                11,
                "Duplicate field");
    }

    @Test
    public void malformed() throws Exception {
        send("""
                {""",
                RETURN,
                1,
                2,
                "close marker for Object");
    }

    @Test
    public void empty() throws Exception {
        send("", CONTINUE);
    }

    @Test
    public void tooLong() throws Exception {
        send("[" + repeat("\"0123456\",", 1024) + "\"x\"]",
                RETURN,
                1,
                8003,
                "Exceeded maxSize.");
    }

    @Test
    public void justNotTooLong() throws Exception {
        send("[" + repeat("\"0123456\",", 1023) + "\"x\"]",
                CONTINUE);
    }

    @Test
    public void tooDeep() throws Exception {
        send(repeat("{\"a\":", 11) + "1" + repeat("}", 11),
                RETURN,
                1,
                52,
                "Exceeded maxDepth.");
    }

    @Test
    public void justNotTooDeep() throws Exception {
        send(repeat("{\"a\":", 10) + "1" + repeat("}", 10),
                CONTINUE);
    }

    @Test
    public void stringTooLong() throws Exception {
        send("[\"" + repeat("1", 21) + "\"]",
                RETURN,
                1,
                25,
                "Exceeded maxStringLength.");
    }

    @Test
    public void stringJustNotTooLong() throws Exception {
        send("[\"" + repeat("1", 20) + "\"]",
                CONTINUE);
    }

    @Test
    public void keyTooLong() throws Exception {
        send("{\"01234567890\": \"" + repeat("1", 20) + "\"}",
                RETURN,
                1,
                18,
                "Exceeded maxKeyLength.");
    }

    @Test
    public void keyTooLong2() throws Exception {
        send("{\"0123456789\": { \"01234567890\": \"" + repeat("1", 20) + "\"} }",
                RETURN,
                1,
                34,
                "Exceeded maxKeyLength.");
    }

    @Test
    public void keyTooLong3() throws Exception {
        send("{\"0123456789\": [ { \"01234567890\": \"" + repeat("1", 20) + "\"} ] }",
                RETURN,
                1,
                36,
                "Exceeded maxKeyLength.");
    }

    @Test
    public void keyNotTooLong() throws Exception {
        send("{\"0123456789\": [ { \"0123456789\": \"" + repeat("1", 20) + "\"} ] }",
                CONTINUE);
    }

    @Test
    public void objectTooLarge() throws Exception {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 11; i++) {
            if (i != 0)
                sb.append(",");
            sb.append("\"").append(i).append("\": 1");
        }
        sb.append("}");
        send(sb.toString(),
                RETURN,
                1,
                79,
                "Exceeded maxObjectSize.");
    }

    @Test
    public void objectJustNotTooLarge() throws Exception {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 10; i++) {
            if (i != 0)
                sb.append(",");
            sb.append("\"").append(i).append("\": 1");
        }
        sb.append("}");
        send(sb.toString(),
                CONTINUE);
    }

    @Test
    public void arrayTooLarge() throws Exception {
        send("[" + repeat("1,", 2048) + "1]",
                RETURN,
                1,
                4099,
                "Exceeded maxArraySize.");
    }

    @Test
    public void arrayJustNotTooLarge() throws Exception {
        send("[" + repeat("1,", 2047) + "1]",
                CONTINUE);
    }

    @Test
    public void tooManyTokens() throws Exception {
        send("[" + repeat("1,", 2047) + "[" + repeat("1,", 2047) + "1]" + "]",
                RETURN,
                1,
                8192,
                "Exceeded maxTokens.");
    }

    @Test
    public void justNotTooManyTokens() throws Exception {
        send("[" + repeat("1,", 2045) + "[" + repeat("1,", 2045) + "1]" + "]",
                CONTINUE);
    }

    private void send(String body, Outcome expectOut, Object ...parameters) throws Exception {
        Exchange exc = new Request.Builder()
                .post("/")
                .contentType(APPLICATION_JSON)
                .body(body)
                .buildExchange();

        if (expectOut == CONTINUE) {
            assertEquals(expectOut, jpiProd.handleRequest(exc));
            assertNull(exc.getResponse());

            assertEquals(expectOut, jpiDev.handleRequest(exc));
            assertNull(exc.getResponse());
        } else {
            assertEquals(expectOut, jpiProd.handleRequest(exc));
            assertEquals("", exc.getResponse().getBodyAsStringDecoded());

            assertEquals(expectOut, jpiDev.handleRequest(exc));

            System.out.println("exc.getResponse() = " + exc.getResponse());

            ProblemDetails pd = ProblemDetails.parse(exc.getResponse());

            System.out.println("pd = " + pd);

            assertTrue(pd.getDetail().contains(parameters[2].toString()));
            assertEquals("JSON Protection Violation", pd.getTitle());
            assertEquals(parameters[0],pd.getExtensions().get("line"));
            assertEquals(parameters[1], pd.getExtensions().get("column"));
        }
    }
}
