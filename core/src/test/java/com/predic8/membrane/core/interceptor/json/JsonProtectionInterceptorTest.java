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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonProtectionInterceptorTest {
    static JsonProtectionInterceptor jpi;

    @BeforeAll
    public static void init() {
        jpi = new JsonProtectionInterceptor();
    }

    @Test
    public void ok() throws Exception {
        send("""
                {}""",
                Outcome.CONTINUE);
    }

    @Test
    public void ok2() throws Exception {
        send("""
                {"a":"b"}""",
                Outcome.CONTINUE);
    }

    @Test
    public void duplicateKey() throws Exception {
        send("""
                {"a":1,"a":2}""",
                Outcome.RETURN);
    }

    @Test
    public void malformed() throws Exception {
        send("""
                {""",
                Outcome.RETURN);
    }

    @Test
    public void empty() throws Exception {
        send("", Outcome.CONTINUE);
    }

    private void send(String body, Outcome expectedOutcome) throws Exception {
        var e = new Request.Builder().post("/").header("Content-Type", "application/json").body(body).buildExchange();
        Outcome outcome = jpi.handleRequest(e);
        assertEquals(expectedOutcome, outcome);
    }
}
