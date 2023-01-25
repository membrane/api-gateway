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

import com.google.common.base.Strings;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.google.common.base.Strings.repeat;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonProtectionInterceptorTest {
    static JsonProtectionInterceptor jpi;

    @BeforeAll
    public static void init() {
        jpi = new JsonProtectionInterceptor();
        jpi.setMaxTokens(4096);
        jpi.setMaxSize(10240);
        jpi.setMaxDepth(10);
        jpi.setMaxStringLength(20);
        jpi.setMaxKeyLength(10);
        jpi.setMaxObjectSize(10);
        jpi.setMaxArraySize(2048);
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
    public void duplicateKey() throws Exception {
        send("""
                {"a":1,"a":2}""",
                RETURN);
    }

    @Test
    public void malformed() throws Exception {
        send("""
                {""",
                RETURN);
    }

    @Test
    public void empty() throws Exception {
        send("", CONTINUE);
    }

    @Test
    public void tooLong() throws Exception {
        send("[" + repeat("\"0123456\",", 1024) + "\"x\"]",
                RETURN);
    }

    @Test
    public void justNotTooLong() throws Exception {
        send("[" + repeat("\"0123456\",", 1023) + "\"x\"]",
                CONTINUE);
    }

    @Test
    public void tooDeep() throws Exception {
        send(repeat("{\"a\":", 11) + "1" + repeat("}", 11),
                RETURN);
    }

    @Test
    public void justNotTooDeep() throws Exception {
        send(repeat("{\"a\":", 10) + "1" + repeat("}", 10),
                CONTINUE);
    }

    @Test
    public void stringTooLong() throws Exception {
        send("[\"" + repeat("1", 21) + "\"]",
                RETURN);
    }

    @Test
    public void stringJustNotTooLong() throws Exception {
        send("[\"" + repeat("1", 20) + "\"]",
                CONTINUE);
    }

    @Test
    public void keyTooLong() throws Exception {
        send("{\"01234567890\": \"" + repeat("1", 20) + "\"}",
                RETURN);
    }

    @Test
    public void keyTooLong2() throws Exception {
        send("{\"0123456789\": { \"01234567890\": \"" + repeat("1", 20) + "\"} }",
                RETURN);
    }

    @Test
    public void keyTooLong3() throws Exception {
        send("{\"0123456789\": [ { \"01234567890\": \"" + repeat("1", 20) + "\"} ] }",
                RETURN);
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
            sb.append("\"" + i + "\": 1");
        }
        sb.append("}");
        send(sb.toString(),
                RETURN);
    }

    @Test
    public void objectJustNotTooLarge() throws Exception {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 10; i++) {
            if (i != 0)
                sb.append(",");
            sb.append("\"" + i + "\": 1");
        }
        sb.append("}");
        send(sb.toString(),
                CONTINUE);
    }

    @Test
    public void arrayTooLarge() throws Exception {
        send("[" + repeat("1,", 2048) + "1]",
                RETURN);
    }

    @Test
    public void arrayJustNotTooLarge() throws Exception {
        send("[" + repeat("1,", 2047) + "1]",
                CONTINUE);
    }

    @Test
    public void tooManyTokens() throws Exception {
        send("[" + repeat("1,", 2047) + "[" + repeat("1,", 2047) + "1]" + "]",
                RETURN);
    }

    @Test
    public void justNotTooManyTokens() throws Exception {
        send("[" + repeat("1,", 2045) + "[" + repeat("1,", 2045) + "1]" + "]",
                CONTINUE);
    }


    private void send(String body, Outcome expectedOutcome) throws Exception {
        var e = new Request.Builder().post("/").header("Content-Type", "application/json").body(body).buildExchange();
        Outcome outcome = jpi.handleRequest(e);
        assertEquals(expectedOutcome, outcome);
    }
}
