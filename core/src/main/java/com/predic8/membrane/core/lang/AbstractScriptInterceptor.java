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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import java.io.*;
import java.util.*;
import java.util.function.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

public abstract class AbstractScriptInterceptor extends AbstractInterceptor {

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
        if ("".equals(src))
            return;
        initInternal();
    }

    protected abstract void initInternal() throws IOException, ClassNotFoundException;

    protected Outcome runScript(Exchange exc, Flow flow) throws InterruptedException, IOException, ClassNotFoundException {

        Object res = script.apply(getParameterBindings(exc, flow));

        if (res instanceof Outcome) {
            return (Outcome) res;
        }

        if (res instanceof Response) {
            exc.setResponse((Response) res);
            return RETURN;
        }

        if (res instanceof Request) {
            exc.setRequest((Request) res);
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
        }
        parameters.put("spring", router.getBeanFactory());
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
            case RESPONSE -> exc.getResponse();
            default -> null;
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