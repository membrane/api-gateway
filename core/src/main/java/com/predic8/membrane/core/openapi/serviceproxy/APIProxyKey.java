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

package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.lang.spel.*;
import com.predic8.membrane.core.rules.*;
import org.slf4j.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.*;

import java.util.*;

import static java.util.Optional.ofNullable;

public class APIProxyKey extends ServiceProxyKey {

    private static final Logger log = LoggerFactory.getLogger(APIProxyKey.class.getName());

    private final ArrayList<String> basePaths = new ArrayList<>();

    private Expression testExpr;

    public APIProxyKey(RuleKey key, String test, boolean openAPI) {
        super(key);
        init(test, openAPI);
        setUsePathPattern(true);
    }

    public APIProxyKey(String ip, String host, int port, String path, String method, String test, boolean openAPI) {
        super(host, method, path, port, ip);
        init(test, openAPI);
        setUsePathPattern(true);
    }

    protected void init(String test, boolean openAPI) {
        if (test != null)
            testExpr = new SpelExpressionParser().parseExpression(test);

        if (!openAPI)
            return;

        // Add basePaths of OpenAPIPublisherInterceptor to accept them also
        basePaths.add(OpenAPIPublisherInterceptor.PATH);    // new path
        basePaths.add(OpenAPIPublisherInterceptor.PATH_UI); // "
        basePaths.add("/api-doc");                          // old to stay compatible
        basePaths.add("/api-doc/ui");                       // "

    }

    @Override
    public boolean complexMatch(Exchange exc) {
        if (!testCondition(exc))
            return false;

        if (basePaths.isEmpty())
            return true;

        var uri = exc.getRequest().getUri();
        for (String basePath : basePaths) {
            if (!uri.startsWith(basePath))
                continue;

            log.debug("Rule matches {}", uri);
            return true;

        }
        return false;
    }

    private boolean testCondition(Exchange exc) {
        if (testExpr == null)
            return true;
        Boolean result = testExpr.getValue(new ExchangeEvaluationContext(exc, exc.getRequest()), Boolean.class);
        return result != null && result;
    }

    void addBasePaths(ArrayList<String> paths) {
        basePaths.addAll(paths);
    }

    public String getKeyId() {
        return (
                getMethod() + "-"
                + ofNullable(getIp()).orElse("0.0.0.0") + "-"
                + getHost()
                + getPort()
                + getPath() + "-"
                + (testExpr == null ? "true" : testExpr.getExpressionString())
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj))
            return false;
        if (obj instanceof APIProxyKey other) {
            if (!basePaths.equals(other.basePaths))
                return false;
            return testExpr.equals(other.testExpr);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + testExpr.hashCode() + basePaths.hashCode();
    }
}
