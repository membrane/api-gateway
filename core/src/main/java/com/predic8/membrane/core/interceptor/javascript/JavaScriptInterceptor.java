/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.javascript;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TextUtil;
import org.apache.commons.lang.StringEscapeUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;

@MCElement(name = "javascript", mixed = true)
public class JavaScriptInterceptor extends AbstractInterceptor {
    private String src = "";
    private String modifiedScript;

    ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

    public JavaScriptInterceptor(){
        name = "Javascript";
    }


    @Override
    public void init() throws Exception {
        if (router == null)
            return;
        if ("".equals(src))
            return;

        modifiedScript = getScriptWithImports();
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return runScript(exc, Flow.REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return runScript(exc, Flow.RESPONSE);
    }

    @Override
    public void handleAbort(Exchange exc) {
        try {
            runScript(exc, Flow.ABORT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
    }

    private Outcome runScript(Exchange exc, Flow flow) throws InterruptedException, ScriptException {
        addScriptParameters(exc, flow);

        Object res = runScript();

        if (res instanceof Outcome) {
            return (Outcome) res;
        }

        if (res instanceof Response) {
            exc.setResponse((Response) res);
            return Outcome.RETURN;
        }

        if (res instanceof Request) {
            exc.setRequest((Request) res);
        }
        return Outcome.CONTINUE;
    }

    private String getScriptWithImports() {
        return "var imports = new JavaImporter(com.predic8.membrane.core.interceptor.Outcome," +
                "com.predic8.membrane.core.http" +
                ")\n" +
                "with(imports){\n" +
                        getSrc() +
                        "\n}";
    }

    private Object runScript() throws ScriptException {
        return engine.eval(modifiedScript);
    }

    private void addScriptParameters(Exchange exc, Flow flow) {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("exc", exc);
        parameters.put("flow", flow);
        parameters.put("spring", router.getBeanFactory());

        for(String name : parameters.keySet())
            engine.put(name,parameters.get(name));
    }


    public String getSrc() {
        return src;
    }

    @MCTextContent
    public void setSrc(String src) {
        this.src = src;
    }

    @Override
    public String getShortDescription() {
        return "Executes a Javascript script.";
    }

    @Override
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtil.removeFinalChar(getShortDescription()));
        sb.append(":<br/><pre style=\"overflow-x:auto\">");
        sb.append(StringEscapeUtils.escapeHtml(TextUtil.removeCommonLeadingIndentation(src)));
        sb.append("</pre>");
        return sb.toString();
    }
}
