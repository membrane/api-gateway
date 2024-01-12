/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.flow;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.groovy.*;
import org.slf4j.*;

import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.*;
import static com.predic8.membrane.core.lang.ScriptingUtils.createParameterBindings;

/**
 * @description <p>
 * The "if" interceptor supports conditional execution of a group of executors.
 * </p>
 *
 * <p>
 * Note that this is a draft implementation only: Design decissions are still pending.
 * </p>
 * <ul>
 * <li>'evaluate condition only once': Should the condition be reevaluated once response handling has begun?</li>
 * <li>'evaluate condition always during request handling already' (even when 'if' is nested in 'response')</li>
 * <li>What happens to ABORT handling of interceptor A in <code>&lt;request&gt;&lt;if test="..."&gt;&lt;A /&gt;&lt;/if&gt;&lt;/response&gt;</code></li>
 * </ul>
 */
@MCElement(name = "if")
public class ConditionalInterceptor extends AbstractFlowInterceptor {
    private static final Logger log = LoggerFactory.getLogger(InterceptorFlowController.class);

    // configuration
    private String test;
    private LanguageType language = GROOVY;

    // state
    private final InterceptorFlowController interceptorFlowController = new InterceptorFlowController();
    private Function<Map<String, Object>, Boolean> condition;

    public enum LanguageType {
        GROOVY,
    }

    public ConditionalInterceptor() {
        name = "Conditional Interceptor";
    }

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        condition = new GroovyLanguageSupport().compileExpression(router.getBackgroundInitializator(), null, test);
    }

    private boolean testCondition(Exchange exc) {
        HashMap<String, Object> parameters = new HashMap<>() {{
            put("Outcome", Outcome.class);
            put("RETURN", RETURN);
            put("CONTINUE", CONTINUE);
            put("ABORT", Outcome.ABORT);
            put("spring", router.getBeanFactory());
            put("exc", exc);
            putAll(createParameterBindings(router.getUriFactory(), exc, exc.getRequest(), REQUEST, false));
        }};
        return condition.apply(parameters);
    }

    @Override
    public Outcome handleRequest(Exchange exchange) throws Exception {

        boolean handleRequest = testCondition(exchange);
        if (log.isDebugEnabled())
            log.debug("Expression evaluated to " + handleRequest);

        if (handleRequest)
            return interceptorFlowController.invokeRequestHandlers(exchange, getInterceptors());

        return CONTINUE;
    }

    public LanguageType getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @example groovy
     */
    @MCAttribute
    public void setLanguage(LanguageType language) {
        this.language = language;
    }

    public String getTest() {
        return test;
    }

    /**
     * @description the condition to be tested
     * @example exc.request.header.userAgentSupportsSNI
     */
    @Required
    @MCAttribute
    public void setTest(String test) {
        this.test = test;
    }

    @Override
    public String getShortDescription() {
        StringBuilder ret = new StringBuilder("if (" + test + ") {");
        for (Interceptor i : getInterceptors()) {
            ret.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;").append(i.getDisplayName());
        }
        ret.append("<br/>}");
        return ret.toString();
    }
}
