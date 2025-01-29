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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import static com.predic8.membrane.core.util.TextUtil.*;
import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.text.StringEscapeUtils.*;

/**
 * @description Executes a Javascript. The script can access and manipulate data from the request and response.
 * Use this or the Groovy plugin to extend the functions of Membrane by scripting. See the samples in examples/javascript.
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "javascript", mixed = true)
public class JavascriptInterceptor extends AbstractScriptInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JavascriptInterceptor.class);

    protected LanguageAdapter adapter;

    public JavascriptInterceptor() {
        name = "javascript";
    }

    protected void initInternal() {
        // For tests to set an adapter from outside.
        if (adapter == null)
            adapter = LanguageAdapter.instance(router);
        try {
            script = adapter.compileScript(src);
        } catch (Exception e) {
            throw new ConfigurationException("Could not compile: \n" + src,e);
        }
    }

    @Override
    public String getShortDescription() {
        return "Executes Javascript.";
    }

    @Override
    public String getLongDescription() {
        return removeFinalChar(getShortDescription()) +
               """
               :<br/><pre style="overflow-x:auto">""" +
               escapeHtml4(src.stripIndent()) +
               "</pre>";
    }

    protected void handleScriptExecutionException(Exchange exc, Exception e) {
        log.warn("Error executing {} script: {}", name , e.getMessage());
        log.warn("Script: {}", src);

        ProblemDetails pd = adapter.getProblemDetails(e);
        pd.title("Error executing script.");
        pd.internal("source", trim(src));

        exc.setResponse(pd.build());
    }
}
