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

package com.predic8.membrane.core.lang.groovy;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.lang.CommonBuiltInFunctions;
import groovy.lang.GroovyObjectSupport;

import java.util.List;

/**
 * Helper class for built-in functions that delegates to the implementation CommonBuiltInFunctions.
 *  Difference to SpEL:
 *  The functions are called with ${fn.functionname()} instead of ${functionname()} in the template interceptor
 */
public class GroovyBuiltInFunctions extends GroovyObjectSupport {

    private final Exchange exchange;

    private final Flow flow;

    public GroovyBuiltInFunctions(Exchange exchange, Flow flow) {
        this.exchange = exchange;
        this.flow = flow;
    }

    public String user() {
        return CommonBuiltInFunctions.user(exchange);
    }

    public Object jsonPath(String jsonPath) {
        return CommonBuiltInFunctions.jsonPath(jsonPath, exchange.getMessage(flow));
    }

    public List<String> scopes() {
        return CommonBuiltInFunctions.scopes(exchange);
    }

    public List<String> scopes(String securityScheme) {
        return CommonBuiltInFunctions.scopes(securityScheme, exchange);
    }

    public boolean hasScope(String scope) {
        return CommonBuiltInFunctions.hasScope(scope, exchange);
    }

    public boolean hasScope() {
        return CommonBuiltInFunctions.hasScope(exchange);
    }

    public boolean hasScope(List<String> scopes) {
        return CommonBuiltInFunctions.hasScope(scopes, exchange);
    }

    public boolean isXML() {
        return CommonBuiltInFunctions.isXML(exchange, flow);
    }

    public boolean isJSON() {
        return CommonBuiltInFunctions.isJSON(exchange, flow);
    }

    public String base64Encode(String s) {
        return CommonBuiltInFunctions.base64Encode(s);
    }

    public boolean isBearerAuthorization() {
        return CommonBuiltInFunctions.isBearerAuthorization(exchange);
    }

    public boolean weight(double weightInPercent) {
        return CommonBuiltInFunctions.weight(weightInPercent);
    }

    public boolean isLoggedIn(String beanName) {
        return CommonBuiltInFunctions.isLoggedIn(beanName, exchange);
    }

    public long getDefaultSessionLifetime(String beanName) {
        return CommonBuiltInFunctions.getDefaultSessionLifetime(beanName, exchange);
    }

}