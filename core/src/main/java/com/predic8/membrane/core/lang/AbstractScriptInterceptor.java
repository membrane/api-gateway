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

package com.predic8.membrane.core.lang;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import groovy.lang.*;
import org.graalvm.polyglot.*;
import org.jetbrains.annotations.*;
import org.apache.commons.io.IOUtils;
import org.slf4j.*;

import java.io.InputStream;
import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.lang.ScriptingUtils.*;
import static com.predic8.membrane.core.resolver.ResolverMap.combine;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.lang3.StringUtils.*;

public abstract class AbstractScriptInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractScriptInterceptor.class);

    protected final static ObjectMapper om = new ObjectMapper();

    protected String src;
    protected Function<Map<String, Object>, Object> script;
    private boolean scriptAccessesJson;
    protected String location;

    @Override
    public Outcome handleRequest(Exchange exc) {
        return runScript(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return runScript(exc, RESPONSE);
    }

    public void init() {
        super.init();
        if (router == null)
            throw new RuntimeException("ScriptInterceptors need router instance!");

        if (getLocation() != null) {
            if (getSrc() != null && !getSrc().isBlank()) {
                throw new ConfigurationException("On %s, src and location cannot be set at the same time.".formatted(getDisplayName()));
            }
        }

        src = getScriptSrc();

        scriptAccessesJson = src.contains("json");
        initInternal();
    }

    protected abstract void initInternal();

    @SuppressWarnings("rawtypes")
    protected Outcome runScript(Exchange exc, Flow flow) {

        var msg = getMessage(exc, flow);

        Object res;
        try {
            res = script.apply(getParameterBindings(exc, flow, msg));
        } catch (Exception e) {
            handleScriptExecutionException(exc, e);
            return ABORT;
        }

        if (res instanceof Outcome outcome) {
            return outcome;
        }

        if (res instanceof Response response) {
            exc.setResponse(response);
            return RETURN;
        }

        if (res instanceof Request request) {
            exc.setRequest(request);
        }

        if (res instanceof Map m) {
            msg = createResponseAndToExchangeIfThereIsNone(exc, flow, msg);
            msg.getHeader().setContentType(APPLICATION_JSON);
            try {
                msg.setBodyContent(om.writeValueAsBytes(m));
            } catch (JsonProcessingException e) {
                log.error("", e);
                internal(router.getConfiguration().isProduction(), getDisplayName())
                        .addSubSee("json-processing-1")
                        .detail("Error serializing Map to JSON")
                        .exception(e)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            return CONTINUE;
        }

        if (res instanceof byte[] bytes) {
            msg = createResponseAndToExchangeIfThereIsNone(exc, flow, msg);
            msg.setBodyContent(bytes);
            return CONTINUE;
        }

        if (res instanceof String s) {
            if (s.equals("undefined")) {
                return CONTINUE;
            }
            msg = createResponseAndToExchangeIfThereIsNone(exc, flow, msg);
            msg.setBodyContent(s.getBytes(UTF_8));
            return CONTINUE;
        }

        if (res == null) {
            return CONTINUE;
        }

        // Test for package name is needed cause the dependency is provided and maybe not on the classpath
        if (res.getClass().getPackageName().startsWith("org.graalvm.polyglot") && res instanceof Value value) {
            Map m = value.as(Map.class);
            msg.getHeader().setContentType(APPLICATION_JSON);
            try {
                msg.setBodyContent(om.writeValueAsBytes(m));
            } catch (JsonProcessingException e) {
                log.error("", e);
                internal(router.getConfiguration().isProduction(), getDisplayName())
                        .addSubSee("json-processing-2")
                        .detail("Error serializing Map to JSON")
                        .exception(e)
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            return CONTINUE;
        }
        return CONTINUE;
    }

    /**
     * If a script returns a String or a Map that should be interpreted as a successful message (200 OK) if there
     * is not a message already.
     * Design issue: Method does to things!
     *
     * @param exchange Current Exchange
     * @param flow     Flow
     * @param msg      Current Message
     * @return Message message of the exchange or newly created Response message
     */
    private static @Nullable Message createResponseAndToExchangeIfThereIsNone(Exchange exchange, Flow flow, Message msg) {
        if (msg != null)
            return msg;
        if (flow.isResponse()) {
            var response = ok().build();
            exchange.setResponse(response);
            return response;
        }
        return null;
    }

    protected void handleScriptExecutionException(Exchange exc, Exception e) {
        log.warn("Error executing {} script: {}\n{}", name, e.getMessage(), src);
        log.debug("", e);

        var pd = internal(router.getConfiguration().isProduction(), getDisplayName())
                .addSubSee("script-execution")
                .title("Error executing script.")
                .addSubType("scripting");

        if (e instanceof MissingPropertyException mpe) {
            pd.internal("missingProperty", mpe.getProperty());
            pd.stacktrace(false);
        }

        pd.exception(e)
                .internal("source", trim(src))
                .buildAndSetResponse(exc);
    }

    private Map<String, Object> getParameterBindings(Exchange exc, Flow flow, Message msg) {
        var binding = createParameterBindings(router, exc, flow, scriptAccessesJson && msg.isJSON());
        addOutcomeObjects(binding);
        return binding;
    }

    @Override
    public void handleAbort(Exchange exc) {
        try {
            runScript(exc, Flow.ABORT);
        } catch (Exception e) {
            log.error("", e);
        }
    }

    private void addOutcomeObjects(Map<String, Object> parameters) {
        parameters.put("Outcome", Outcome.class);
        parameters.put("RETURN", RETURN);
        parameters.put("CONTINUE", CONTINUE);
        parameters.put("ABORT", ABORT);
    }

    String getScriptSrc() {
        if (src != null)
            return src;
        if (location != null) {
            try (InputStream is = router.getResolverMap().resolve(combine(router.getConfiguration().getBaseLocation(), location))) {
                return IOUtils.toString(is, UTF_8);
            } catch (Exception e) {
                throw new ConfigurationException("Could not read script from %s".formatted(location), e);
            }
        }
        throw new RuntimeException("Script must have a src or location!");
    }

    public String getSrc() {
        return src;
    }

    @MCTextContent
    public void setSrc(String src) {
        this.src = src;
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description A file or URL location where the content that should be set as body could be found
     * @default N/A
     * @example conf/body.txt
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }
}
