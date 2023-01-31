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

import com.fasterxml.jackson.databind.*;
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

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
public class JavascriptInterceptorTest {

    private final static ObjectMapper om = new ObjectMapper();

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
        executeScript("var x = 1;", engine);
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void scriptWithError(Class<LanguageSupport> engine) {
        assertThrows(Exception.class, () -> executeScript("var x=;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void exchangeAccess(Class<LanguageSupport> engine) throws Exception {
        executeScript("var e = exc;", engine);
        executeScript("var e = flow;", engine);
        executeScript("var e = spring;", engine);
        executeScript("var e = message;", engine);
        executeScript("var e = header;", engine);
        executeScript("var e = body;", engine);
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void wrongScript(Class<LanguageSupport> engine) throws Exception {
        assertEquals(CONTINUE, executeScript("var x = 1/0;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void outcomeContinue(Class<LanguageSupport> engine) throws Exception {
        assertEquals(CONTINUE, executeScript("CONTINUE", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void outcomeReturn(Class<LanguageSupport> engine) throws Exception {
        assertEquals(RETURN, executeScript("RETURN;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setRequestHeader(Class<LanguageSupport> engine) throws Exception {
        executeScript("header.setValue('baz','7');", engine);
        assertEquals("7", exc.getRequest().getHeader().getFirstValue("baz"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setRequest(Class<LanguageSupport> engine) throws Exception {
        executeScript("exc.setRequest(new Request.Builder().body('foo').build());", engine);
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnRequest(Class<LanguageSupport> engine) throws Exception {
        executeScript("new Request.Builder().body('foo').build();", engine);
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setResponse(Class<LanguageSupport> engine) throws Exception {
        executeScript("exc.setResponse(Response.ok('baz').build())", engine);
        assertEquals("baz", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnResponse(Class<LanguageSupport> engine) throws Exception {
        executeScript("Response.ok('baz').build()", engine);
        assertEquals("baz", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setReturnMAPAsJSONResponse(Class<LanguageSupport> engine) throws Exception {
        executeScript("""
                Response.ok().body({"foo": 7}).build();
                """, engine);
        assertEquals("{\"foo\":7}", exc.getResponse().getBodyAsStringDecoded());
    }



    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnObject(Class<LanguageSupport> engine) throws Exception {
        // Object must be nested in () otherwise it won't compile
        executeScript("""
               ({id:7, name: 'Roller', desc: 'äöüÄÖÜ'});
                """, engine);

        Map<String,Object> m = om.readValue(exc.getRequest().getBodyAsStringDecoded(),Map.class);
        assertEquals(7, m.get("id"));
        assertEquals("Roller", m.get("name"));
        assertEquals("äöüÄÖÜ", m.get("desc"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void testProperties(Class<LanguageSupport> engine) throws Exception {

        exc.getProperties().put("answer","42");

        executeScript("""
				properties.put('thing','towel');
				header.add('answer',properties.get('answer'));
				CONTINUE;
				""", engine);

        assertEquals("42", exc.getRequest().getHeader().getFirstValue("answer"));
        assertEquals("towel", exc.getProperty("thing"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void jsonRequest(Class<LanguageSupport> engine) throws Exception {

        exc.getRequest().setBodyContent("""
				{"id":7,"city":"Bonn"}""".getBytes());
        exc.getRequest().getHeader().setContentType(APPLICATION_JSON);

        executeScript("""
                console.log(json);
                console.log(json['id']);
                header.add('id','id-'+json.id);
                header.add('city',json.city);
                """, engine);

        assertEquals("id-7", exc.getRequest().getHeader().getFirstValue("id"));
        assertEquals("Bonn", exc.getRequest().getHeader().getFirstValue("city"));
    }


    @SafeVarargs
    private Outcome executeScript(String src, Class<LanguageSupport>... engine) throws Exception {
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