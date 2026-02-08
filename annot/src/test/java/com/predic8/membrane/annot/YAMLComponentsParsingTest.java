/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.annot;

import com.predic8.membrane.annot.beanregistry.*;
import com.predic8.membrane.annot.util.*;
import com.predic8.membrane.annot.yaml.*;
import com.predic8.membrane.common.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static com.predic8.membrane.annot.SpringConfigurationXSDGeneratingAnnotationProcessorTest.*;
import static com.predic8.membrane.annot.util.CompilerHelper.*;
import static com.predic8.membrane.annot.util.StructureAssertionUtil.*;
import static com.predic8.membrane.common.ExceptionUtil.*;
import static org.junit.jupiter.api.Assertions.*;

public class YAMLComponentsParsingTest {

    @Test
    void componentsEmpty() {
        assertStructure(
                parse("""
                        components: {}
                        """),
                clazz("Components")
        );
    }

    @Test
    void componentsAndApiEmptySameDocument() {
        assertStructure(
                parseDocs("""
                        components: {}
                        ---
                        api: {}
                        """),
                clazz("Components"),
                clazz("ApiElement")
        );
    }


    @Test
    void componentsSingleDefinition() {
        assertStructure(
                parse("""
                        components:
                          sad:
                            demoBean: {}
                        """),
                clazz("Components")
        );
    }

    @Test
    public void refAsFlowListItem() {
        assertStructure(
                parse("""
                        components:
                          auth1:
                            basicAuthentication:
                              fileUserDataProvider:
                                htpasswdPath: /etc/htpasswd
                        ---
                        api:
                          flow:
                            - $ref: "#/components/auth1"
                        """),
                clazz("Components"),
                clazz("ApiElement",
                        property("flow", list(
                                clazz("BasicAuthenticationElement",
                                        property("fileUserDataProvider",
                                                clazz("FileUserDataProviderElement",
                                                        property("htpasswdPath", value("/etc/htpasswd")))))))));
    }

    @Test
    public void refAndInlineMixInFlow() {
        assertStructure(
                parse("""
                        components:
                          auth1:
                            basicAuthentication:
                              fileUserDataProvider:
                                htpasswdPath: /etc/htpasswd
                        ---
                        api:
                          flow:
                            - $ref: "#/components/auth1"
                            - template:
                                location: classpath:/t.xml
                        """),
                clazz("Components"),
                clazz("ApiElement",
                        property("flow", list(
                                clazz("BasicAuthenticationElement",
                                        property("fileUserDataProvider",
                                                clazz("FileUserDataProviderElement",
                                                        property("htpasswdPath", value("/etc/htpasswd"))))),
                                clazz("TemplateElement",
                                        property("location", value("classpath:/t.xml")))
                        )))
        );
    }

    @Test
    public void sameRefUsedMultipleTimesInFlow() {
        assertStructure(
                parse("""
                        components:
                          auth1:
                            basicAuthentication:
                              fileUserDataProvider:
                                htpasswdPath: /etc/htpasswd
                        ---
                        api:
                          flow:
                            - $ref: "#/components/auth1"
                            - $ref: "#/components/auth1"
                        """),
                clazz("Components"),
                clazz("ApiElement",
                        property("flow", list(
                                clazz("BasicAuthenticationElement",
                                        property("fileUserDataProvider",
                                                clazz("FileUserDataProviderElement",
                                                        property("htpasswdPath", value("/etc/htpasswd"))))),
                                clazz("BasicAuthenticationElement",
                                        property("fileUserDataProvider",
                                                clazz("FileUserDataProviderElement",
                                                        property("htpasswdPath", value("/etc/htpasswd")))))
                        )))
        );
    }

    @Test
    public void refListItemWithExtraPropertyError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  auth1:
                    basicAuthentication:
                      fileUserDataProvider:
                        htpasswdPath: /etc/htpasswd
                ---
                api:
                  flow:
                    - $ref: "#/components/auth1"
                      template:
                        location: classpath:/x.xml
                """));
        assertAnyErrorContains(ex, "$ref");
    }

    @Test
    public void refToUnknownComponentIdError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  auth1:
                    basicAuthentication:
                      fileUserDataProvider:
                        htpasswdPath: /etc/htpasswd
                ---
                api:
                  flow:
                    - $ref: "#/components/doesNotExist"
                """));
        assertAnyErrorContains(ex, "doesNotExist");
    }

    @Test
    public void invalidRefPointerError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  auth1:
                    basicAuthentication:
                      fileUserDataProvider:
                        htpasswdPath: /etc/htpasswd
                ---
                api:
                  flow:
                    - $ref: "#/not-components/auth1"
                """));
        assertAnyErrorContains(ex, "Reference #/not-components/auth1 not found");
    }

    @Test
    void componentDefinitionWithUnknownComponentKeyError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  x:
                    doesNotExist: {}
                """));
        var c = getRootCause(ex);
        if (c instanceof ConfigurationParsingException cpe) {
            var pc = cpe.getParsingContext();
            assertEquals("$.components.x", pc.path());
            assertNull(pc.getKey());
            assertTrue(cpe.getMessage().contains("Expected exactly one key"));
            assertTrue(cpe.getMessage().contains("0"));
        } else {
            fail("Expected ConfigurationParsingException but got: " + c);
        }
    }

    @Test
    void componentDefinitionWithNoComponentKeyError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  x: {}
                """));
        var c = getRootCause(ex);
        if (c instanceof ConfigurationParsingException cpe) {
            var pc = cpe.getParsingContext();
            assertEquals("$.components.x", pc.path());
            assertNull(pc.getKey());
            assertTrue(cpe.getMessage().contains("Expected exactly one key"));
            assertTrue(cpe.getMessage().contains("0"));
        } else {
            fail("Expected ConfigurationParsingException but got: " + c);
        }
    }

    @Test
    void componentDefinitionWithMultipleComponentKeysError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  x:
                    demoBean: {}
                    basicAuthentication:
                      fileUserDataProvider:
                        htpasswdPath: /etc/htpasswd
                """));
        var c = getRootCause(ex);
        if (c instanceof ConfigurationParsingException cpe) {
            var pc = cpe.getParsingContext();
            assertEquals("$.components.x", pc.path());
            assertNull(pc.getKey());
            assertTrue(cpe.getMessage().contains("Expected exactly one key"));
            assertTrue(cpe.getMessage().contains("2"));

        } else {
            fail("Expected ConfigurationParsingException but got: " + c);
        }
    }

    @Test
    void refInsideObjectLevel() {
        assertStructure(
                parse("""
                        components:
                          manager:
                            bearerToken:
                              header: Authorization
                        ---
                        api:
                          flow:
                            - oauth2authserver:
                                issuer: https://issuer
                                otherFields: abc
                                $ref: "#/components/manager"
                        """),
                clazz("Components"),
                clazz("ApiElement",
                        property("flow", list(
                                clazz("OAuth2AuthServerElement",
                                        property("issuer", value("https://issuer")),
                                        property("otherFields", value("abc")),
                                        property("bearerToken",
                                                clazz("BearerTokenElement",
                                                        property("header", value("Authorization")))))
                        )))
        );
    }

    @Test
    public void objectLevelRefAndInlineForbidden() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  manager:
                    bearerToken:
                      header: Authorization
                ---
                api:
                  flow:
                    - oauth2authserver:
                        issuer: https://issuer
                        bearerToken:
                          header: Inline
                        $ref: "#/components/manager"
                """));
        assertAnyErrorContains(ex, "Cannot use '$ref' together with inline 'bearerToken' in 'oauth2authserver'.");
    }

    @Test
    public void objectLevelRefTypeMismatchError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  manager:
                    basicAuthentication:
                      fileUserDataProvider:
                        htpasswdPath: /etc/htpasswd
                ---
                api:
                  flow:
                    - oauth2authserver:
                        issuer: https://issuer
                        $ref: "#/components/manager"
                """));
        assertAnyErrorContains(ex, "Referenced component '#/components/manager' (type 'basicAuthentication') is not allowed in 'oauth2authserver'.");
    }

    @Test
    public void flowRefTypeMismatchError() {
        var ex = assertThrows(RuntimeException.class, () -> parse("""
                components:
                  manager:
                    bearerToken:
                      header: Authorization
                ---
                api:
                  flow:
                    - $ref: "#/components/manager"
                """));
        assertAnyErrorContains(ex, "Value of type 'bearerToken' is not allowed in list 'flow'. Expected 'FlowItem'.");
    }

    @Test
    public void componentRefersToAnotherComponent() {
        assertStructure(
                parse("""
                        components:
                          manager:
                            bearerToken:
                              header: Authorization
                          oauth1:
                            oauth2authserver:
                              issuer: https://issuer
                              otherFields: abc
                              $ref: "#/components/manager"
                        ---
                        api:
                          flow:
                            - $ref: "#/components/oauth1"
                        """),
                clazz("Components"),
                clazz("ApiElement",
                        property("flow", list(
                                clazz("OAuth2AuthServerElement",
                                        property("issuer", value("https://issuer")),
                                        property("otherFields", value("abc")),
                                        property("bearerToken",
                                                clazz("BearerTokenElement",
                                                        property("header", value("Authorization")))))
                        )))
        );
    }

    @Test
    public void componentIdIsWordFromGrammar() {
        assertStructure(
                parse("""
                        components:
                          bearerToken:
                            bearerToken:
                              header: Authorization
                        ---
                        api:
                          flow:
                            - oauth2authserver:
                                issuer: https://issuer
                                otherFields: abc
                                $ref: "#/components/bearerToken"
                        """),
                clazz("Components"),
                clazz("ApiElement",
                        property("flow", list(
                                clazz("OAuth2AuthServerElement",
                                        property("issuer", value("https://issuer")),
                                        property("otherFields", value("abc")),
                                        property("bearerToken",
                                                clazz("BearerTokenElement",
                                                        property("header", value("Authorization")))))
                        )))
        );
    }

    @Test
    public void topLevelElementNotAllowedAsNestedChild() {
        // Not covered by GenericYamlParser
        var ex = assertThrows(RuntimeException.class, () -> parseWithTopLevelOnlySources("""
            outer:
              items:
                - topThing: {}
            """));
        assertSchemaErrorContains(ex, "property 'topThing' is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    public void topLevelElementStillAllowedAtRoot() {
        assertStructure(
                parseWithTopLevelOnlySources("""
                        topThing: {}
                        """),
                clazz("TopThingElement")
        );
    }

    @Test
    public void nonTopLevelElementAllowedAsNestedChild() {
        assertStructure(
                parseWithTopLevelOnlySources("""
                        outer:
                          items:
                            - inner: {}
                        """),
                clazz("OuterElement",
                        property("items", list(
                                clazz("InnerElement")
                        )))
        );
    }

    private BeanRegistry parseWithTopLevelOnlySources(String yaml) {
        var sources = splitSources(MC_MAIN_DEMO + COMPONENTS_DEMO_SOURCES + TOPLEVEL_ONLY_SOURCES);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);
        return parseYAML(result, yaml);
    }

    private static final String TOPLEVEL_ONLY_SOURCES = """
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import java.util.List;
            
            @MCElement(name="outer", topLevel=true, component=false)
            public class OuterElement {
                List<ItemBase> items;
            
                public List<ItemBase> getItems() {
                    return items;
                }
            
                @MCChildElement
                public void setItems(List<ItemBase> items) {
                    this.items = items;
                }
            }
            ---
            package com.predic8.membrane.demo;
            public abstract class ItemBase {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="inner")
            public class InnerElement extends ItemBase {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="topThing", topLevel=true, component=false)
            public class TopThingElement extends ItemBase {}
            """;


    private List<?> parseDocs(String yamlWithDocs) {
        var sources = splitSources(MC_MAIN_DEMO + COMPONENTS_DEMO_SOURCES);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);

        return splitYamlDocs(yamlWithDocs).stream()
                .map(doc -> parseYAML(result, doc))
                .flatMap(r -> r.getBeans().stream())
                .toList();
    }

    private List<String> splitYamlDocs(String yaml) {
        return java.util.regex.Pattern.compile("(?m)^---\\s*$")
                .splitAsStream(yaml)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private BeanRegistry parse(String yaml) {
        var sources = splitSources(MC_MAIN_DEMO + COMPONENTS_DEMO_SOURCES);
        var result = CompilerHelper.compile(sources, false);
        assertCompilerResult(true, result);
        return parseYAML(result, yaml);
    }

    private void assertSchemaErrorContains(RuntimeException ex, String... needles) {
        var root = getCause(ex);
        if (!(root instanceof YamlSchemaValidationException yse))
            throw new AssertionError("Expected YamlSchemaValidationException but got: " + root, root);

        assertFalse(yse.getErrors().isEmpty(), "Expected schema errors.");
        var msg = yse.getErrors().getFirst().toString();
        for (var n : needles)
            assertTrue(msg.contains(n), () -> "Expected error to contain '" + n + "' but was: " + msg);
    }

    private void assertAnyErrorContains(RuntimeException ex, String... needles) {
        var root = getCause(ex);
        var msg = root.getMessage() != null ? root.getMessage() : root.toString();
        for (var n : needles)
            assertTrue(msg.toLowerCase().contains(n.toLowerCase()), () -> "Expected error to contain '" + n + "' but was: " + msg);
    }

    private Throwable getCause(Throwable e) {
        if (e.getCause() != null)
            return getCause(e.getCause());
        return e;
    }

    private static final String COMPONENTS_DEMO_SOURCES = """
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            import java.util.List;
            
            @MCElement(name="api", topLevel=true, component=false)
            public class ApiElement {
                List<FlowItem> flow;
            
                public List<FlowItem> getFlow() {
                    return flow;
                }
            
                @MCChildElement
                public void setFlow(List<FlowItem> flow) {
                    this.flow = flow;
                }
            }
            ---
            package com.predic8.membrane.demo;
            public abstract class FlowItem {}
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="basicAuthentication")
            public class BasicAuthenticationElement extends FlowItem {
                FileUserDataProviderElement fileUserDataProvider;
            
                public FileUserDataProviderElement getFileUserDataProvider() {
                    return fileUserDataProvider;
                }
            
                @MCChildElement
                public void setFileUserDataProvider(FileUserDataProviderElement fileUserDataProvider) {
                    this.fileUserDataProvider = fileUserDataProvider;
                }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="fileUserDataProvider", component=false)
            public class FileUserDataProviderElement {
                String htpasswdPath;
            
                public String getHtpasswdPath() {
                    return htpasswdPath;
                }
            
                @MCAttribute
                public void setHtpasswdPath(String htpasswdPath) {
                    this.htpasswdPath = htpasswdPath;
                }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="template")
            public class TemplateElement extends FlowItem {
                String location;
            
                public String getLocation() {
                    return location;
                }
            
                @MCAttribute
                public void setLocation(String location) {
                    this.location = location;
                }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="oauth2authserver")
            public class OAuth2AuthServerElement extends FlowItem {
                String issuer;
                String otherFields;
                BearerTokenElement bearerToken;
            
                public String getIssuer() {
                    return issuer;
                }
            
                @MCAttribute
                public void setIssuer(String issuer) {
                    this.issuer = issuer;
                }
            
                public String getOtherFields() {
                    return otherFields;
                }
            
                @MCAttribute
                public void setOtherFields(String otherFields) {
                    this.otherFields = otherFields;
                }
            
                public BearerTokenElement getBearerToken() {
                    return bearerToken;
                }
            
                @MCChildElement
                public void setBearerToken(BearerTokenElement bearerToken) {
                    this.bearerToken = bearerToken;
                }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="bearerToken")
            public class BearerTokenElement {
                String header;
            
                public String getHeader() {
                    return header;
                }
            
                @MCAttribute
                public void setHeader(String header) {
                    this.header = header;
                }
            }
            ---
            package com.predic8.membrane.demo;
            import com.predic8.membrane.annot.*;
            
            @MCElement(name="demoBean")
            public class BeanElement {
            }
            """;
}
