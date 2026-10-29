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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.LanguageSupport;
import com.predic8.membrane.core.lang.javascript.GraalVMJavascriptSupport;
import com.predic8.membrane.core.lang.javascript.RhinoJavascriptLanguageSupport;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.ProblemDetailsTestUtil.parse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
public class JavascriptInterceptorTest {

    private final static ObjectMapper om = new ObjectMapper();

    final DefaultRouter router = new DefaultRouter();
    JavascriptInterceptor interceptor;
    Exchange exc;

    @BeforeEach
    void setup() {
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
        Class.forName("org.mozilla.javascript.Context");
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
    void simpleScript(Class<LanguageSupport> engine) {
        executeScript("var x = 1;", engine);
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void scriptWithError(Class<LanguageSupport> engine) throws Exception {
        Outcome outcome= executeScript("adsfasf;", engine);
        assertEquals(ABORT, outcome);
        assertEquals(500, exc.getResponse().getStatusCode());
        ProblemDetails pd = parse(exc.getResponse());
        assertEquals(500, pd.getStatus());
        System.out.println("pd = " + pd);
        assertTrue(pd.getType().startsWith("https://membrane-api.io/problems/internal"));
        assertTrue(pd.getTitle().contains("Error executing"));
        assertEquals(1,pd.getInternal().get("line"));
        assertEquals(1,pd.getInternal().get("column"));
        assertNotNull(pd.getInternal().get("engine"));
    }

    @ParameterizedTest
    @ValueSource(classes = RhinoJavascriptLanguageSupport.class)
    void logsLineWithError(Class<LanguageSupport> engine) {
        final var root = (Logger) LogManager.getRootLogger();
        final var appender = new TestAppender("JavascriptInterceptorTest");
        appender.start();
        root.addAppender(appender);
        try {
            executeScript("""
                    var a = 1;
                    adsfasf;
                    var b = 2;""", engine);
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        assertTrue(appender.getMessages().contains("Error in Line: adsfasf;"), appender.getMessages().toString());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void exchangeAccess(Class<LanguageSupport> engine) {
        executeScript("var e = exc;", engine);
        executeScript("var e = flow;", engine);
        executeScript("var e = spring;", engine);
        executeScript("var e = message;", engine);
        executeScript("var e = header;", engine);
        executeScript("var e = body;", engine);
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void wrongScript(Class<LanguageSupport> engine) {
        assertEquals(CONTINUE, executeScript("var x = 1/0;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void outcomeContinue(Class<LanguageSupport> engine) {
        assertEquals(CONTINUE, executeScript("CONTINUE", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void outcomeReturn(Class<LanguageSupport> engine) {
        assertEquals(RETURN, executeScript("RETURN;", engine));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setRequestHeader(Class<LanguageSupport> engine) {
        executeScript("header.put('baz','7');", engine);
        assertEquals("7", exc.getRequest().getHeader().getFirstValue("baz"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setRequest(Class<LanguageSupport> engine) {
        executeScript("exc.setRequest(new Request.Builder().body('foo').build());", engine);
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnRequest(Class<LanguageSupport> engine) {
        executeScript("new Request.Builder().body('foo').build();", engine);
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setResponse(Class<LanguageSupport> engine) {
        executeScript("exc.setResponse(Response.ok('baz').build())", engine);
        assertEquals("baz", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnResponse(Class<LanguageSupport> engine) {
        executeScript("Response.ok('baz').build()", engine);
        assertEquals("baz", exc.getResponse().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void setReturnMAPAsJSONResponse(Class<LanguageSupport> engine) {
        executeScript("""
                Response.ok().body({"foo": 7}).build();
                """, engine);
        // The Rhino interpreter passes every number as a Double
        final var expected = engine.equals(RhinoJavascriptLanguageSupport.class) ? "{\"foo\":7.0}" : "{\"foo\":7}";
        assertEquals(expected, exc.getResponse().getBodyAsStringDecoded());
    }



    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnObject(Class<LanguageSupport> engine) throws Exception {
        // Object must be nested in () otherwise it won't compile
        executeScript("""
               ({id:7, name: 'Roller', desc: 'äöüÄÖÜ'});
               """, engine);

        Map<String,Object> m = om.readValue(exc.getRequest().getBodyAsStringDecoded(),Map.class);
        assertEquals(7, ((Number) m.get("id")).intValue());
        assertEquals("Roller", m.get("name"));
        assertEquals("äöüÄÖÜ", m.get("desc"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void testProperties(Class<LanguageSupport> engine) {

        exc.getProperties().put("answer","42");

        executeScript("""
				property.put('thing','towel');
				header.add('answer',property.get('answer'));
				CONTINUE;
				""", engine);

        assertEquals("42", exc.getRequest().getHeader().getFirstValue("answer"));
        assertEquals("towel", exc.getProperty("thing"));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    public void jsonRequest(Class<LanguageSupport> engine) {

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

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void jsonOfPreviousRequestDoesNotLeak(Class<LanguageSupport> engine) {
        initInterceptor("header.put('json-id', typeof json === 'object' ? '' + json.id : 'none');", engine);

        // More runs than pooled engines, so every engine has seen a JSON request before the non-JSON ones
        final var runs = 4 * Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < runs; i++)
            assertEquals("7", jsonIdSeenByScript(new Request.Builder().body("{\"id\":7}").contentType(APPLICATION_JSON).build()));
        for (int i = 0; i < runs; i++)
            assertEquals("none", jsonIdSeenByScript(new Request.Builder().body("foo").contentType(TEXT_PLAIN).build()));
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void topLevelVariableOfPreviousRequestDoesNotLeak(Class<LanguageSupport> engine) {
        initInterceptor("""
                var counter = typeof counter === 'number' ? counter + 1 : 1;
                header.put('counter', '' + counter);
                """, engine);

        for (int i = 0; i < 4 * Runtime.getRuntime().availableProcessors(); i++) {
            final var exchange = new Exchange(null);
            exchange.setRequest(new Request.Builder().body("foo").build());
            interceptor.handleRequest(exchange);
            assertEquals("1", exchange.getRequest().getHeader().getFirstValue("counter"));
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void builtInObjectOfPreviousRequestDoesNotLeak(Class<LanguageSupport> engine) {
        initInterceptor("""
                header.put('leak', '' + ({}).leak);
                Object.prototype.leak = 'x';
                """, engine);

        for (int i = 0; i < 4 * Runtime.getRuntime().availableProcessors(); i++) {
            final var exchange = new Exchange(null);
            exchange.setRequest(new Request.Builder().body("foo").build());
            interceptor.handleRequest(exchange);
            assertEquals("undefined", exchange.getRequest().getHeader().getFirstValue("leak"));
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void taggedTemplateOfPreviousRequestDoesNotLeak(Class<LanguageSupport> engine) {
        // Rhino caches the strings array of a tagged template in the compiled script, with the Array.prototype of
        // the execution that created it
        initInterceptor("""
                Array.prototype.requestId = function() { return header.get('id'); };
                function tag(strings) { return strings.requestId(); }
                header.put('seen', tag`x`);
                """, engine);

        for (int i = 0; i < 4 * Runtime.getRuntime().availableProcessors(); i++) {
            final var exchange = new Exchange(null);
            exchange.setRequest(new Request.Builder().header("id", "id-" + i).body("foo").build());
            interceptor.handleRequest(exchange);
            assertEquals("id-" + i, exchange.getRequest().getHeader().getFirstValue("seen"));
        }
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void print(Class<LanguageSupport> engine) {
        assertEquals(CONTINUE, executeScript("print('a', 1); CONTINUE;", engine));
    }

    /**
     * Separated by spaces as by the print and console.log of GraalVM, whose output can not be captured here.
     */
    @ParameterizedTest
    @ValueSource(classes = RhinoJavascriptLanguageSupport.class)
    void printAndConsoleLogPrintAllArguments(Class<LanguageSupport> engine) {
        final var out = new ByteArrayOutputStream();
        final var stdout = System.out;
        System.setOut(new PrintStream(out, true, UTF_8));
        try {
            executeScript("print('a', 1, undefined); console.log('b', 2); CONTINUE;", engine);
        } finally {
            System.setOut(stdout);
        }
        assertEquals(List.of("a 1 undefined", "b 2"), out.toString(UTF_8).lines().toList());
    }

    @ParameterizedTest
    @ValueSource(classes = RhinoJavascriptLanguageSupport.class)
    void consoleLogPrintsObjectsAsJson(Class<LanguageSupport> engine) {
        exc.getRequest().setBodyContent("{\"id\":7}".getBytes());
        exc.getRequest().getHeader().setContentType(APPLICATION_JSON);

        final var out = new ByteArrayOutputStream();
        final var stdout = System.out;
        System.setOut(new PrintStream(out, true, UTF_8));
        try {
            assertEquals(CONTINUE, executeScript("""
                    console.log(json, [1, 'a'], null);
                    var r = {}; r.self = r;
                    console.log(r);
                    var m = new java.util.HashMap(); m.put('k', 'v');
                    console.log(m);
                    console.log(new TypeError('boom'));
                    console.log({toJSON: function() {}});
                    CONTINUE;""", engine));
        } finally {
            System.setOut(stdout);
        }
        assertEquals(List.of("{\"id\":7} [1,\"a\"] null", "[object Object]", "{k=v}", "TypeError: boom", "[object Object]"), out.toString(UTF_8).lines().toList());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void undefinedResultKeepsBody(Class<LanguageSupport> engine) {
        exc.getRequest().setBodyContent("foo".getBytes());
        assertEquals(CONTINUE, executeScript("var x = 1;", engine));
        assertEquals("foo", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnString(Class<LanguageSupport> engine) {
        assertEquals(CONTINUE, executeScript("'bar'", engine));
        assertEquals("bar", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnNestedObject(Class<LanguageSupport> engine) {
        executeScript("({a: {b: [1, 'x']}})", engine);
        assertEquals("{\"a\":{\"b\":[1,\"x\"]}}", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void returnArray(Class<LanguageSupport> engine) {
        assertEquals(CONTINUE, executeScript("['x', {a: 'y'}, ['z']]", engine));
        assertTrue(exc.getRequest().isJSON());
        assertEquals("[\"x\",{\"a\":\"y\"},[\"z\"]]", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void returnJavaByteArray() {
        assertEquals(CONTINUE, executeScript("Java.to([104, 105], 'byte[]')"));
        assertEquals("hi", exc.getRequest().getBodyAsStringDecoded());
    }

    /**
     * Only with GraalVM json is a Java map, Rhino passes a Javascript object.
     */
    @Test
    void returnJavaMapContainingObject() {
        exc.getRequest().setBodyContent("{\"id\":7}".getBytes());
        exc.getRequest().getHeader().setContentType(APPLICATION_JSON);

        assertEquals(CONTINUE, executeScript("json.put('added', {value: [7]}); json;"));
        assertEquals("{\"id\":7,\"added\":{\"value\":[7]}}", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void returnJavaSetAndArrayContainingObjects() {
        exc.getRequest().setBodyContent("{\"id\":7}".getBytes());
        exc.getRequest().getHeader().setContentType(APPLICATION_JSON);

        assertEquals(CONTINUE, executeScript("""
                var set = new java.util.HashSet();
                set.add({v: 1});
                json.put('set', set);
                json.put('array', Java.to([{v: 2}], 'java.lang.Object[]'));
                json;"""));
        assertEquals("{\"id\":7,\"set\":[{\"v\":1}],\"array\":[{\"v\":2}]}", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "var r = {}; r.self = r; r;",
            "var r = {}; r.a = r; r.b = [r, r]; r;",
            "var o = {}; for (var i = 0; i < 100000; i++) o = {c: o}; o;",
    })
    void unserializableResult(String src) {
        assertEquals(ABORT, executeScript(src));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @Test
    void cyclicJavaMapResult() {
        exc.getRequest().setBodyContent("{\"id\":7}".getBytes());
        exc.getRequest().getHeader().setContentType(APPLICATION_JSON);

        assertEquals(ABORT, executeScript("json.put('self', json); json;"));
        assertEquals(500, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void sharedObjectIsNoCycle(Class<LanguageSupport> engine) {
        executeScript("var s = {v: 1}; ({a: s, b: [s]});", engine);
        assertEquals("{\"a\":{\"v\":1},\"b\":[{\"v\":1}]}", exc.getRequest().getBodyAsStringDecoded());
    }

    @ParameterizedTest
    @ValueSource(classes = {GraalVMJavascriptSupport.class, RhinoJavascriptLanguageSupport.class})
    void stringifiedObjectStoredInProperty(Class<LanguageSupport> engine) {
        executeScript("property.put('order', JSON.stringify({id: 7})); CONTINUE;", engine);
        assertEquals("{\"id\":7}", exc.getProperty("order"));
    }

    private String jsonIdSeenByScript(Request request) {
        final var exchange = new Exchange(null);
        exchange.setRequest(request);
        interceptor.handleRequest(exchange);
        return exchange.getRequest().getHeader().getFirstValue("json-id");
    }

    @SafeVarargs
    private Outcome executeScript(String src, Class<LanguageSupport>... engine) {
        initInterceptor(src, engine);
        return interceptor.handleRequest(exc);
    }

    @SafeVarargs
    private void initInterceptor(String src, Class<LanguageSupport>... engine) {
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
    }
}