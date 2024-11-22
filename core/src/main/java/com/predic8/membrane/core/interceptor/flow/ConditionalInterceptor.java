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
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.groovy.*;
import com.predic8.membrane.core.lang.spel.*;
import org.slf4j.*;
import org.springframework.expression.*;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.*;

import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.flow.ConditionalInterceptor.LanguageType.*;
import static com.predic8.membrane.core.lang.ScriptingUtils.*;
import static java.util.UUID.*;

/**
 * @description <p>
 * The "if" interceptor supports conditional execution of a group of executors.
 * </p>
 *
 * <p>
 * Note that this is a draft implementation only: Design decisions are still pending.
 * </p>
 * <ul>
 * <li>'evaluate condition only once': Should the condition be reevaluated once response handling has begun?</li>
 * <li>'evaluate condition always during request handling already' (even when 'if' is nested in 'response')</li>
 * <li>What happens to ABORT handling of interceptor A in <code>&lt;request&gt;&lt;if test="..."&gt;&lt;A /&gt;&lt;/if&gt;&lt;/response&gt;</code></li>
 * </ul>
 */
@MCElement(name = "if")
public class ConditionalInterceptor extends AbstractFlowInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ConditionalInterceptor.class);

    // configuration
    private String test;
    private LanguageType language = GROOVY;


    /**
     * Spring Expression Language
     * SpEL configuration with MIXED mode allows both interpreted and compiled expression evaluation.
     * Compiled only did not work with jsonPath
     */
    private final SpelParserConfiguration spelConfig = new SpelParserConfiguration(SpelCompilerMode.MIXED, this.getClass().getClassLoader());
    private Expression spelExpr;

    private final InterceptorFlowController interceptorFlowController = new InterceptorFlowController();
    private Function<Map<String, Object>, Boolean> condition;

    public enum LanguageType {
        GROOVY,
        SPEL
    }

    public ConditionalInterceptor() {
        name = "Conditional Interceptor";
    }

    @Override
    public void init(Router router) throws Exception {
        super.init(router);

        switch (language) {
            case GROOVY ->
                    condition = new GroovyLanguageSupport().compileExpression(router.getBackgroundInitializator(), null, test);
            case SPEL -> spelExpr = new SpelExpressionParser(spelConfig).parseExpression(test);

        }
    }

    private boolean testCondition(Exchange exc, Message msg, Flow flow) {

        switch (language) {
            case GROOVY -> {
                return condition.apply(getParametersForGroovy(exc, msg, flow));
            }
            case SPEL -> {
                Boolean result = spelExpr.getValue(new ExchangeEvaluationContext(exc, msg), Boolean.class);
                return result != null && result;
            }
            default -> {
                log.error("Should not happen!");
                return false;
            }
        }
    }

    private HashMap<String, Object> getParametersForGroovy(Exchange exc, Message msg, Flow flow) {
        return new HashMap<>() {{
            put("Outcome", Outcome.class);
            put("RETURN", RETURN);
            put("CONTINUE", CONTINUE);
            put("ABORT", ABORT);
            put("spring", router.getBeanFactory());
            put("exc", exc);
            putAll(createParameterBindings(router.getUriFactory(), exc, msg, flow, false));
        }};
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getRequest(), REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getResponse(), RESPONSE);
    }

    // Unique id of this interceptors instance.
    // Avoids child interceptors being pushed to the stack twice
    private final String uuid = "if-" + randomUUID();

    private Outcome handleInternal(Exchange exc, Message msg, Flow flow) throws Exception {

        boolean result = testCondition(exc, msg, flow);
        if (log.isDebugEnabled())
            log.debug("Expression evaluated to " + result);

        if (result) {
            switch (flow) {
                case REQUEST -> {
                    exc.setProperty(uuid, true);
                    return interceptorFlowController.invokeRequestHandlers(exc, getInterceptors());
                }
                case RESPONSE -> {
                    if (conditionWasFalseOrNotExecutedInRequestFlow(exc))
                        for (Interceptor i : getInterceptors()) {
                            exc.pushInterceptorToStack(i);
                        }
                }
            }
        }
        return CONTINUE;
    }

    private boolean conditionWasFalseOrNotExecutedInRequestFlow(Exchange exchange) {
        return exchange.getProperty(uuid) == null;
    }

    public LanguageType getLanguage() {
        return language;
    }

    /**
     * @description the language of the 'test' condition
     * @default groovy
     * @example SpEL, groovy
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