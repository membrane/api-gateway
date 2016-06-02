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

import com.google.common.base.Function;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.javascript.JavascriptLanguageSupport;
import com.predic8.membrane.core.util.ClassFinder;
import com.predic8.membrane.core.util.TextUtil;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

@MCElement(name = "javascript", mixed = true)
public class JavascriptInterceptor extends AbstractInterceptor {

    private String src = "";

    private Function<Map<String, Object>, Object> script;
    private JavascriptLanguageSupport jls;
    private HashMap<String, Object> implicitClasses;

    public JavascriptInterceptor() {
        name = "Javascript";
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
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init() throws IOException, ClassNotFoundException {
        if (router == null)
            return;
        if ("".equals(src))
            return;

        jls = new JavascriptLanguageSupport();
        implicitClasses = getJavascriptTypesForHttpClasses();
        script = jls.compileScript(router, src);

    }

    private Outcome runScript(Exchange exc, Flow flow) throws InterruptedException, IOException, ClassNotFoundException {
        HashMap<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("exc", exc);
        parameters.put("flow", flow);
        parameters.put("spring", router.getBeanFactory());
        addOutcomeObjects(parameters);

        parameters.putAll(implicitClasses);
        Object res = script.apply(parameters);

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

    private HashMap<String, Object> getJavascriptTypesForHttpClasses() throws IOException, ClassNotFoundException {
        return getJavascriptTypesForClasses(getHttpPackageClasses());
    }

    private HashMap<String, Object> getJavascriptTypesForClasses(HashMap<String, Object> classes) {
        HashMap<String, Object> result = new HashMap<>();
        for(Object clazz : classes.values()){
            Class<?> clazzz = (Class<?>) clazz;
            String scriptSrc = clazzz.getSimpleName() + ".static;";
            //TODO this is hacky, do this differently ( maybe do this one time at startup )
            Object jsType = jls.compileScript(router, scriptSrc).apply(classes);

            result.put(clazzz.getSimpleName(),jsType);
        }
        return result;
    }

    private void addOutcomeObjects(HashMap<String, Object> parameters) {
        parameters.put("Outcome", Outcome.class);
        parameters.put("RETURN", Outcome.RETURN);
        parameters.put("CONTINUE", Outcome.CONTINUE);
        parameters.put("ABORT", Outcome.ABORT);
    }

    private HashMap<String, Object> getHttpPackageClasses() throws IOException, ClassNotFoundException {
        String httpPackage = "com.predic8.membrane.core.http";
        HashMap<String, Object> result = new HashMap<>();
        List<Class<?>> classes = ClassFinder.find(router.getBeanFactory().getClassLoader(), httpPackage);
        for(Class c : classes) {
            if(c.getPackage().getName().equals(httpPackage) && !c.getSimpleName().isEmpty())
                result.put(c.getSimpleName(), c);
        }
        return result;
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
