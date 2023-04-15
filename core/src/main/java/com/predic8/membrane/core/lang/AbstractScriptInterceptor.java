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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.graalvm.polyglot.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ScriptingUtils.*;

public abstract class AbstractScriptInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractScriptInterceptor.class.getName());

    protected final static ObjectMapper om = new ObjectMapper();

    protected String src;
    protected Function<Map<String, Object>, Object> script;
    private boolean scriptAccessesJson;

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return runScript(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return runScript(exc, RESPONSE);
    }

    public void init() throws IOException, ClassNotFoundException {
        if (router == null)
            return;
        if (src.isEmpty())
            return;
        scriptAccessesJson = src.contains("json");
        initInternal();
    }

    protected abstract void initInternal() throws IOException, ClassNotFoundException;

    @SuppressWarnings("rawtypes")
    protected Outcome runScript(Exchange exc, Flow flow) throws InterruptedException, IOException, ClassNotFoundException {

        Message msg = getMessage(exc, flow);

        Object res;
        try {
            res = script.apply(getParameterBindings(exc, flow, msg));
        } catch (Exception e) {
            log.warn("Error executing script: {}",e);
            Map<String,Object> details = new HashMap<>();
            details.put("message","See logs for details.");
            exc.setResponse(createProblemDetails(500, "/internal-error", "Internal Server Error", details));
            return RETURN;
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

        if(res instanceof Map m) {
            msg.getHeader().setContentType(APPLICATION_JSON);
            msg.setBodyContent(om.writeValueAsBytes(m));
            return CONTINUE;
        }

        if(res instanceof String s) {
            if (s.equals("undefined")) {
                return CONTINUE;
            }
            msg.getHeader().setContentType(TEXT_HTML);
            msg.setBodyContent(om.writeValueAsBytes(s));
            return CONTINUE;
        }

        if(res == null) {
            return CONTINUE;
        }

        // Test for packagename is needed cause the dependency is provided and maybe not on the classpath
        if(res.getClass().getPackageName().startsWith("org.graalvm.polyglot") && res instanceof Value value) {
            Map m = value.as(Map.class);
            msg.getHeader().setContentType(APPLICATION_JSON);
            msg.setBodyContent(om.writeValueAsBytes(m));
            return CONTINUE;
        }

        return CONTINUE;
    }

    private HashMap<String, Object> getParameterBindings(Exchange exc, Flow flow, Message msg) {
        HashMap<String, Object> parameterBindings = createParameterBindings(exc, msg, flow, scriptAccessesJson && msg.isJSON());
        addOutcomeObjects(parameterBindings);
        parameterBindings.put("spring", router.getBeanFactory());
        return parameterBindings;
    }

    @Override
    public void handleAbort(Exchange exc) {
        try {
            runScript(exc, Flow.ABORT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void addOutcomeObjects(HashMap<String, Object> parameters) {
        parameters.put("Outcome", Outcome.class);
        parameters.put("RETURN", RETURN);
        parameters.put("CONTINUE", CONTINUE);
        parameters.put("ABORT", Outcome.ABORT);
    }

    public String getSrc() {
        return src;
    }

    @MCTextContent
    public void setSrc(String src) {
        this.src = src;
    }
}