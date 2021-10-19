/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.kubernetes;

import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxy;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;

public class GenericJsonParserTest {

    @Test
    public void testCombined() {
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("port", 2020);
            put("blockRequest", true);
            put("path", new HashMap<String, Object>() {{
                put("isRegExp", true);
                put("value", "/foo");
            }});
            put("interceptors", Arrays.asList(
                    new HashMap<String, Object>() {{
                        put("groovy", new HashMap<String, Object>() {{
                            put("src", "println()");
                        }});
                    }},
                    new HashMap<String, Object>() {{
                        put("target", new HashMap<String, Object>() {{
                            put("host", "localhost");
                            put("port", 8080);
                        }});
                    }}
            ));
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);

        assertEquals(2020, sp.getPort());
        assertTrue(sp.isBlockRequest());
        assertTrue(sp.getPath().isRegExp());
        assertEquals("/foo", sp.getPath().getValue());

        assertEquals(2, sp.getInterceptors().size());
        assertTrue(sp.getInterceptors().get(0) instanceof GroovyInterceptor);
        assertEquals("println()", ((GroovyInterceptor) sp.getInterceptors().get(0)).getSrc());
        assertTrue(sp.getInterceptors().get(1) instanceof AbstractServiceProxy.Target);
        assertEquals("localhost", ((AbstractServiceProxy.Target) sp.getInterceptors().get(1)).getHost());
        assertEquals(8080, ((AbstractServiceProxy.Target) sp.getInterceptors().get(1)).getPort());
    }

    @Test
    public void testMultipleChildElements() {
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("interceptors", Arrays.asList(
                    new HashMap<String, Object>() {{
                        put("groovy", new HashMap<String, Object>() {{
                            put("src", "println()");
                        }});
                    }},
                    new HashMap<String, Object>() {{
                        put("target", new HashMap<String, Object>() {{
                            put("host", "localhost");
                            put("port", 8080);
                        }});
                    }}
            ));
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);
        assertEquals(2, sp.getInterceptors().size());
        assertTrue(sp.getInterceptors().get(0) instanceof GroovyInterceptor);
        assertEquals("println()", ((GroovyInterceptor) sp.getInterceptors().get(0)).getSrc());
        assertTrue(sp.getInterceptors().get(1) instanceof AbstractServiceProxy.Target);
        assertEquals("localhost", ((AbstractServiceProxy.Target) sp.getInterceptors().get(1)).getHost());
        assertEquals(8080, ((AbstractServiceProxy.Target) sp.getInterceptors().get(1)).getPort());
    }

    @Test
    public void testChildElements() {
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("interceptors", Collections.singletonList(
                    new HashMap<String, Object>() {{
                        put("groovy", new HashMap<String, Object>() {{
                            put("src", "println()");
                        }});
                    }}
            ));
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);
        assertFalse(sp.getInterceptors().isEmpty());
        assertEquals("println()", ((GroovyInterceptor) sp.getInterceptors().get(0)).getSrc());
    }

    @Test
    public void testChildElement() {
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("path", new HashMap<String, Object>() {{
                put("isRegExp", true);
                put("value", "/abb");
            }});
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);

        assertTrue(sp.getPath().isRegExp());
        assertEquals("/abb", sp.getPath().getValue());
    }

    @Test
    public void testTextContent() {
        String want = "println(\"Hello, World!\")";
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("src", want);
        }});

        GroovyInterceptor gi = GenericJsonParser.parse(GroovyInterceptor.class, spec);
        assertEquals(want, gi.getSrc());
    }

    @Test
    public void testAttributeInteger() {
        int want = 3000;
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("port", want);
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);

        assertEquals(want, sp.getPort());
    }

    @Test
    public void testAttributeString() {
        String want = "192.168.133.7";
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("host", want);
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);

        assertEquals(want, sp.getHost());
    }

    @Test
    public void testAttributeBoolean() {
        JSONObject spec = new JSONObject(new HashMap<String, Object>() {{
            put("blockRequest", true);
        }});

        ServiceProxy sp = GenericJsonParser.parse(ServiceProxy.class, spec);

        assertTrue(sp.isBlockRequest());
    }
}