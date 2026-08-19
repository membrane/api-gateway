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
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.proxies.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.PATH;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisherInterceptor.PATH_UI;
import static com.predic8.membrane.core.openapi.util.UriTemplateMatcher.matchTemplate;
import static java.util.Optional.*;

public class APIProxyKey extends ServiceProxyKey {

    private static final Logger log = LoggerFactory.getLogger(APIProxyKey.class.getName());

    private final ArrayList<String> basePaths = new ArrayList<>();

    /**
     * Paths of the OpenAPIPublisherInterceptor (e.g. /api-docs). These stay reachable even when a
     * custom path is configured, but - unlike {@link #basePaths} - they must not override the
     * configured path when discriminating between several APIs sharing the same base path.
     */
    private final ArrayList<String> apiDocsPaths = new ArrayList<>();

    /**
     * For complex matches use SpEL
     */
    private ExchangeExpression exchangeExpression;

    public APIProxyKey(RuleKey key, ExchangeExpression exchangeExpression, boolean openAPI) {
        super(key);
        init(exchangeExpression, openAPI);
        setUsePathPattern(true);
    }

    public APIProxyKey(int port) {
        this(null, "*", port, null, "*", null, true);
    }

    public APIProxyKey(String ip, String host, int port, String path, String method, ExchangeExpression exchangeExpression, boolean openAPI) {
        super(host, method, path, port, ip);
        init(exchangeExpression, openAPI);
        setUsePathPattern(true);
    }

    protected void init(ExchangeExpression exchangeExpression, boolean openAPI) {
        this.exchangeExpression = exchangeExpression;

        if (!openAPI)
            return;

        // Add paths of OpenAPIPublisherInterceptor to accept them also
        apiDocsPaths.add(PATH);    // new path
        apiDocsPaths.add(PATH_UI); // "
        apiDocsPaths.add("/api-doc");                          // old to stay compatible
        apiDocsPaths.add("/api-doc/ui");                       // "
    }

    @Override
    public boolean complexMatch(Exchange exc) {
        try {
            if (!testCondition(exc))
                return false;
        } catch (Exception e) {
            log.warn("Error evaluating test expression '{}' of API '{}'. Ignoring test. Please check configuration. Error was: {}", exchangeExpression.getExpression(), this, e.getMessage());
            return false;
        }

        if (basePaths.isEmpty() && apiDocsPaths.isEmpty())
            return true;

        var uri = exc.getRequest().getUri();
        if (matchesApiDocsPath(uri))
            return true;

        for (String basePath : basePaths) {
            if (!uri.startsWith(basePath))
                continue;

            log.debug("Rule matches {}", uri);
            return true;

        }
        return false;
    }

    private boolean matchesApiDocsPath(String path) {
        for (String apiDocsPath : apiDocsPaths) {
            if (path.startsWith(apiDocsPath))
                return true;
        }
        return false;
    }

    private boolean testCondition(Exchange exc) {
        if (exchangeExpression == null)
            return true;
        return exchangeExpression.evaluate(exc, REQUEST, Boolean.class);
    }

    public void addBasePaths(ArrayList<String> paths) {
        basePaths.addAll(paths);
    }

    public String getKeyId() {
        return (
                getMethod() + "-"
                + ofNullable(getIp()).orElse("0.0.0.0") + "-"
                + getHost()
                + getPort()
                + getPath()
                + (exchangeExpression == null ? "" : "-" + exchangeExpression.getExpression())
        );
    }

    @Override
    public String toString() {
        return super.toString() + (exchangeExpression != null ? " " + exchangeExpression.getExpression() : "");
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj))
            return false;
        if (obj instanceof APIProxyKey other) {
            if (!basePaths.equals(other.basePaths))
                return false;
            return Objects.equals(exchangeExpression, other.exchangeExpression);
        }
        return false;
    }

    @Override
    public boolean matchesPath(String path) {
        // OpenAPI docs stay reachable even when a custom path is configured, see issue #3173.
        if (matchesApiDocsPath(path))
            return true;
        try {
            matchTemplate(getPath(), path); // ignore result
            return true;
        } catch (PathDoesNotMatchException e) {
            log.debug("Path {} doesn't match {}", path,getPath());
        }
        return path.startsWith(getPath());
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hashCode( exchangeExpression.hashCode()) + basePaths.hashCode();
    }
}
