/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.javascript;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.javascript.*;
import org.graalvm.polyglot.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.support.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class JavascriptInterceptorTest {

    Router router = new Router();
    JavascriptInterceptor interceptor;
    Exchange exc;

    @BeforeEach
    public void setup() throws Exception {
        router.setApplicationContext(new GenericApplicationContext());
        interceptor = new JavascriptInterceptor();

        exc = new Exchange(null);
        exc.setRequest(new Request.Builder().header("foo", "42").build());
    }

    /**
     * GraalVM JS should be in the Test dependencies
     */
    @Test
    void isGraalVMPresent() throws ClassNotFoundException {
            Class.forName("org.graalvm.polyglot.Context");
            assertTrue(true);
    }

    @Test
    void isRhinoPresent() throws ClassNotFoundException {
        Class.forName("org.mozilla.javascript.engine.RhinoScriptEngine");
        assertTrue(true);
    }

    @Test
    void error() {
        try (Context context = Context.create()) {
            assertThrows(Exception.class, () -> context.eval("js", "var i =;"));
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void simpleScript(Class<LanguageSupport> engine) throws Exception {
        executeSript("var x = 1", engine);
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void scriptWithError(Class<LanguageSupport> engine) {
        assertThrows(Exception.class, () -> executeSript("var x=;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void exchangeAccess(Class<LanguageSupport> engine) throws Exception {
        executeSript("var e = exc;", engine);
        executeSript("var e = flow;", engine);
        executeSript("var e = spring;", engine);
        executeSript("var e = message;", engine);
        executeSript("var e = header;", engine);
        executeSript("var e = body;", engine);
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void wrongScript(Class<LanguageSupport> engine) throws Exception {
        assertEquals(CONTINUE, executeSript("var x = 1/0;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void outcomeContinue(Class<LanguageSupport> engine) throws Exception {
        assertEquals(CONTINUE, executeSript("CONTINUE", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void outcomeReturn(Class<LanguageSupport> engine) throws Exception {
        assertEquals(RETURN, executeSript("RETURN;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setRequestHeader(Class<LanguageSupport> engine) throws Exception {
        executeSript("header.setValue('baz','7');", engine);
        assertEquals("7", exc.getRequest().getHeader().getFirstValue("baz"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setRequest(Class<LanguageSupport> engine) throws Exception {
        executeSript("exc.setRequest(new Request.Builder().body('foo').build());", engine);
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setReturnRequest(Class<LanguageSupport> engine) throws Exception {
        executeSript("new Request.Builder().body('foo').build();", engine);
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setResponse(Class<LanguageSupport> engine) throws Exception {
        executeSript("exc.setResponse(Response.ok('baz').build())", engine);
        assertEquals("baz", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setReturnResponse(Class<LanguageSupport> engine) throws Exception {
        executeSript("Response.ok('baz').build()", engine);
        assertEquals("baz", exc.getResponse().getBodyAsStringDecoded());
    }

    @SafeVarargs
    private Outcome executeSript(String src, Class<LanguageSupport>... engine) throws Exception {
        interceptor.setSrc(src);

        // Needed because GraalVMJavascriptSupport is not found using Class.forname() in the Interceptor!
        if (engine.length == 0 || engine[0].equals(GraalVMJavascriptSupport.class)) {
            interceptor.adapter = new GraalVMJavascriptLanguageAdapter(router);
            interceptor.adapter.languageSupport = new GraalVMJavascriptSupport(); // Must come before init!
        } else if (engine[0].equals(RhinoJavascriptLanguageSupport.class)) {
            interceptor.adapter = new RhinoJavascriptLanguageAdapter(router);
            interceptor.adapter.languageSupport = new RhinoJavascriptLanguageSupport();
        } else {
            fail("No Javascript Engine");
        }

        interceptor.init(router);
        return interceptor.handleRequest(exc);
    }
}