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

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.FileUtil.*;

public abstract class AbstractScriptInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractScriptInterceptor.class.getName());

    protected final static ObjectMapper om = new ObjectMapper();

    protected String src;
    protected Function<Map<String, Object>, Object> script;

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
        initInternal();
    }

    protected abstract void initInternal() throws IOException, ClassNotFoundException;

    @SuppressWarnings("rawtypes")
    protected Outcome runScript(Exchange exc, Flow flow) throws InterruptedException, IOException, ClassNotFoundException {

        Object res = script.apply(getParameterBindings(exc, flow));

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
            Message msg = getMessage(exc, flow);
            msg.getHeader().setContentType(APPLICATION_JSON);
            msg.setBodyContent(om.writeValueAsBytes(m));
            return CONTINUE;
        }

        // Graal code @Todo move to a Graal class
        if(res instanceof Value value) {
            Map m = value.as(Map.class);
            Message msg = getMessage(exc, flow);
            msg.getHeader().setContentType(APPLICATION_JSON);
            msg.setBodyContent(om.writeValueAsBytes(m));
            return CONTINUE;
        }

        if(res instanceof String s) {
            if (s.equals("undefined")) {
                return CONTINUE;
            }
            Message msg = getMessage(exc,flow);
            msg.getHeader().setContentType(TEXT_HTML);
            msg.setBodyContent(om.writeValueAsBytes(s));
            return CONTINUE;
        }

        return CONTINUE;
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

    private HashMap<String, Object> getParameterBindings(Exchange exc, Flow flow) {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("exc", exc);
        parameters.put("flow", flow);

        Message msg = getMessage(exc, flow);
        if (msg != null) {
            parameters.put("message", msg);
            parameters.put("header", msg.getHeader());
            parameters.put("body", msg.getBody());
            if (msg.isJSON() && src.contains("json")) {
                try {
                    log.info("Parsing body as JSON for scripting plugins");
                    parameters.put("json",om.readValue(readInputStream(msg.getBodyAsStream()),Map.class));  // @Todo not with Javascript
                } catch (Exception e) {
                    log.warn("Can't parse body as JSON: " + e);
                }
            }
        }
        parameters.put("spring", router.getBeanFactory());
        parameters.put("properties", exc.getProperties());

        addOutcomeObjects(parameters);
        return parameters;
    }

    private void addOutcomeObjects(HashMap<String, Object> parameters) {
        parameters.put("Outcome", Outcome.class);
        parameters.put("RETURN", RETURN);
        parameters.put("CONTINUE", CONTINUE);
        parameters.put("ABORT", Outcome.ABORT);
    }

    protected Message getMessage(Exchange exc, Flow flow) {
        return switch (flow) {
            case REQUEST -> exc.getRequest();
            case RESPONSE -> {
                if (exc.getResponse() != null)
                    yield exc.getResponse();
                Response response = Response.ok().build();
                exc.setResponse(response);
                yield response;
            }
            default -> {
                log.info("Should never happen!");
                yield null;
            }
        };
    }

    public String getSrc() {
        return src;
    }

    @MCTextContent
    public void setSrc(String src) {
        this.src = src;
    }
}