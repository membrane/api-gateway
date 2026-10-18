/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.lang;

import com.predic8.membrane.core.config.xml.Namespaces;
import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;

import static com.predic8.membrane.core.http.Request.post;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SetHeaderXPathFunctionTest {

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"SPEL", "GROOVY"})
    void mergesLocalAndGlobalPrefixes(Language language) throws Exception {
        check(language, xmlConfig("g", "urn:customers"), xmlConfig("cust", "urn:customers"),
                "/g:customer/cust:name");
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"SPEL", "GROOVY"})
    void localPrefixOverridesGlobal(Language language) throws Exception {
        check(language, xmlConfig("cust", "urn:other"), xmlConfig("cust", "urn:customers"),
                "//cust:name");
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"SPEL", "GROOVY"})
    void localConfigWithoutGlobal(Language language) throws Exception {
        check(language, null, xmlConfig("cust", "urn:customers"), "//cust:name");
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"SPEL", "GROOVY"})
    void globalConfigWithoutLocal(Language language) throws Exception {
        check(language, xmlConfig("cust", "urn:customers"), null, "//cust:name");
    }

    private void check(Language language, XmlConfig global, XmlConfig local, String xpath) throws Exception {
        var router = new DefaultRouter();
        if (global != null)
            router.getRegistry().register("xmlConfig", global);
        var exchange = post("/customers").xml("""
                <customer xmlns="urn:customers"><name>Alice</name></customer>
                """).buildExchange();
        exchange.setProperty("document", HardenedXmlParser.getInstance()
                .parse(new InputSource(new StringReader(exchange.getRequest().getBodyAsStringDecoded()))));
        // Both xpath() overloads must see the same namespaces.
        for (var context : List.of("", ", property.document")) {
            var interceptor = new SetHeaderInterceptor();
            interceptor.setLanguage(language);
            interceptor.setFieldName("X-Customer");
            var function = language == Language.GROOVY ? "fn.xpath" : "xpath";
            interceptor.setValue("${" + function + "('string(" + xpath + ")'" + context + ")}");
            interceptor.setXmlConfig(local);
            interceptor.init(router);
            assertEquals(com.predic8.membrane.core.interceptor.Outcome.CONTINUE, interceptor.handleRequest(exchange),
                    () -> exchange.getResponse() == null ? "" : exchange.getResponse().getBodyAsStringDecoded());
            assertEquals("Alice", exchange.getRequest().getHeader().getFirstValue("X-Customer"));
        }
        if (global != null)
            assertEquals(global, router.getRegistry().getBean(XmlConfig.class).orElseThrow());
    }

    private static XmlConfig xmlConfig(String prefix, String uri) {
        var namespace = new Namespaces.Namespace();
        namespace.setPrefix(prefix);
        namespace.setUri(uri);
        var namespaces = new Namespaces();
        namespaces.setNamespaces(List.of(namespace));
        var config = new XmlConfig();
        config.setNamespaces(namespaces);
        return config;
    }
}
