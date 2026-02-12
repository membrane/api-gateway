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

package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.lang.groovy.adapted.StreamingTemplateEngine;
import com.predic8.membrane.core.router.TestRouter;
import groovy.text.Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.lang.ScriptingUtils.createParameterBindings;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroovyTemplateEscapingTest {
    private TestRouter r;
    private Exchange e;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        r = new TestRouter();
        e = Request.get("http://localhost/foo").buildExchange();
    }

    @Test
    void noJson() throws IOException, ClassNotFoundException, URISyntaxException {
        Template t = new StreamingTemplateEngine().createTemplate(new StringReader(
                "${flow} $flow <%= flow %> ${hasScope('foo')}"));

        assertEquals("REQUEST REQUEST REQUEST false",
                t.make(createParameterBindings(r, e, REQUEST, false, false))
                        .toString());
    }

    @Test
    void json() throws IOException, ClassNotFoundException, URISyntaxException {
        Template t = new StreamingTemplateEngine().createTemplate(new StringReader(
                "${flow} $flow <%= flow %> ${hasScope('foo')}"));

        assertEquals("\"REQUEST\" \"REQUEST\" \"REQUEST\" false",
                t.make(createParameterBindings(r, e, REQUEST, false, true))
                        .toString());
    }

}
