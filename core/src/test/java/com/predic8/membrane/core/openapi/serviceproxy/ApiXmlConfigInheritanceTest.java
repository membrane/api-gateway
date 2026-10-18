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

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.config.xml.Namespaces;
import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.SpringInterceptor;
import com.predic8.membrane.core.interceptor.apikey.ApiKeysInterceptor;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyExpressionExtractor;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.interceptor.balancer.PolyglotSessionIdExtractor;
import com.predic8.membrane.core.interceptor.flow.IfInterceptor;
import com.predic8.membrane.core.interceptor.flow.choice.Case;
import com.predic8.membrane.core.interceptor.flow.choice.ChooseInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.lang.SetHeaderInterceptor;
import com.predic8.membrane.core.interceptor.templating.TemplateInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpression.Language;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.stream.IntStream;

import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.XPATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiXmlConfigInheritanceTest {
    private final DefaultRouter router = new DefaultRouter();

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"XPATH", "SPEL", "GROOVY"})
    void mergesAllScopesWithNearestPrefixWinning(Language language) throws Exception {
        var global = config("g", "urn:customer", "a", "urn:wrong", "p", "urn:wrong");
        var apiConfig = config("a", "urn:customer", "p", "urn:wrong-api");
        var local = config("p", "urn:customer");
        router.getRegistry().register("xmlConfig", global);
        var header = header(language, local, "concat(/g:customer/a:name, /g:customer/p:name)");
        var api = api(apiConfig, header);
        api.init(router);
        assertHeader(header, exchange(), "AliceAlice");
        assertEquals(1, local.getNamespaces().getNamespaces().size());
        assertEquals("urn:wrong-api", apiConfig.getNamespaces().getNamespaces().get(1).getUri());
        assertEquals("urn:wrong", global.getNamespaces().getNamespaces().get(1).getUri());
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"XPATH", "SPEL", "GROOVY"})
    void inheritsApiWithoutLocalOrGlobalAndKeepsApisIsolated(Language language) throws Exception {
        var first = header(language, null, "//a:name");
        var second = header(language, null, "//a:name");
        api(config("a", "urn:customer"), first).init(router);
        api(config("a", "urn:other"), second).init(router);
        assertHeader(first, exchange(), "Alice");
        assertHeader(second, exchange(), "Bob");
        assertHeader(first, exchange(), "Alice");
        assertNull(first.getXmlConfig());
    }

    @Test
    void inheritsThroughIfElseAndChoose() throws Exception {
        var header = header(SPEL, null, "//a:name");
        var choice = new Case();
        choice.setLanguage(XPATH);
        choice.setTest("//a:name = 'Alice'");
        choice.setFlow(List.of(header));
        var choose = new ChooseInterceptor();
        choose.setChoices(List.of(choice));
        var branch = new IfInterceptor();
        branch.setLanguage(XPATH);
        branch.setTest("not(//a:name)");
        branch.setElse(List.of(choose));
        api(config("a", "urn:customer"), branch).init(router);
        assertHeader(branch, exchange(), "Alice");
    }

    @Test
    void groovyTemplateInheritsApiNamespaces() throws Exception {
        var template = new TemplateInterceptor();
        template.setSrc("${xpath('string(//a:name)')}");
        template.setContentType("text/plain");
        api(config("a", "urn:customer"), template).init(router);
        var exc = exchange();
        assertEquals(CONTINUE, template.handleRequest(exc));
        assertEquals("Alice", exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void targetInheritsApiAndOverridesItLocally() throws Exception {
        var api = api(config("a", "urn:customer", "t", "urn:wrong"));
        var target = api.getTarget();
        target.setXmlConfig(config("t", "urn:customer"));
        target.setUrl("http://localhost/${xpath('string(/a:customer/t:name)')}");
        api.init(router);
        var exc = exchange();
        exc.setDestinations(List.of(target.getUrl()));
        target.applyModifications(exc, router);
        assertEquals(List.of("http://localhost/Alice"), exc.getDestinations());
    }

    @Test
    void apiKeyExtractorInheritsApiNamespaces() throws Exception {
        var extractor = new ApiKeyExpressionExtractor();
        extractor.setLanguage(XPATH);
        extractor.setExpression("string(//a:name)");
        var apiKeys = new ApiKeysInterceptor();
        apiKeys.setExtractors(List.of(extractor));
        api(config("a", "urn:customer"), apiKeys).init(router);
        assertEquals("Alice", extractor.extract(exchange()).orElseThrow().key());
    }

    @Test
    void groovyScriptInheritsApiNamespaces() throws Exception {
        var script = new GroovyInterceptor();
        script.setSrc("header['X-Customer'] = fn.xpath('string(//a:name)'); CONTINUE");
        api(config("a", "urn:customer"), script).init(router);
        assertHeader(script, exchange(), "Alice");
    }

    @Test
    void wrappedPluginInheritsApiNamespaces() throws Exception {
        var wrapper = new SpringInterceptor();
        wrapper.setInner(header(SPEL, null, "//a:name"));
        api(config("a", "urn:customer"), wrapper).init(router);
        assertHeader(wrapper, exchange(), "Alice");
    }

    @Test
    void balancerSessionExtractorInheritsApiNamespaces() throws Exception {
        var extractor = new PolyglotSessionIdExtractor();
        extractor.setLanguage(XPATH);
        extractor.setSessionSource("string(//a:name)");
        var balancer = new LoadBalancingInterceptor();
        balancer.setSessionIdExtractor(extractor);
        api(config("a", "urn:customer"), balancer).init(router);
        assertEquals("Alice", extractor.getSessionId(exchange(), Interceptor.Flow.REQUEST));
    }

    private static APIProxy api(XmlConfig config, Interceptor... flow) {
        var api = new APIProxy();
        api.setXmlConfig(config);
        api.setFlow(List.of(flow));
        return api;
    }

    private static SetHeaderInterceptor header(Language language, XmlConfig config, String xpath) {
        var header = new SetHeaderInterceptor();
        header.setFieldName("X-Customer");
        header.setLanguage(language);
        header.setXmlConfig(config);
        var expression = "string(" + xpath + ")";
        header.setValue("${" + switch (language) {
            case XPATH -> expression;
            case SPEL -> "xpath('" + expression + "')";
            case GROOVY -> "fn.xpath('" + expression + "')";
            default -> throw new IllegalArgumentException();
        } + "}");
        return header;
    }

    private static Exchange exchange() throws Exception {
        return post("/").xml("""
                <customer xmlns="urn:customer"><name>Alice</name><name xmlns="urn:other">Bob</name></customer>
                """).buildExchange();
    }

    private static void assertHeader(Interceptor interceptor, Exchange exc, String expected) {
        assertEquals(CONTINUE, interceptor.handleRequest(exc),
                () -> exc.getResponse() == null ? "" : exc.getResponse().getBodyAsStringDecoded());
        assertEquals(expected, exc.getRequest().getHeader().getFirstValue("X-Customer"));
    }

    private static XmlConfig config(String... pairs) {
        var namespaces = new Namespaces();
        namespaces.setNamespaces(IntStream.range(0, pairs.length / 2).mapToObj(i -> {
            var ns = new Namespaces.Namespace();
            ns.setPrefix(pairs[2 * i]);
            ns.setUri(pairs[2 * i + 1]);
            return ns;
        }).toList());
        var config = new XmlConfig();
        config.setNamespaces(namespaces);
        return config;
    }
}
