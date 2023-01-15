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
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.util.TextUtil.*;
import static org.apache.commons.text.StringEscapeUtils.*;

@MCElement(name = "javascript", mixed = true)
public class JavascriptInterceptor extends AbstractScriptInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JavascriptInterceptor.class);

    protected LanguageAdapter adapter;

    public JavascriptInterceptor() {
        name = "Javascript";
    }

    protected void initInternal() throws IOException, ClassNotFoundException {
        // For tests to set an adapter from outside.
        if (adapter == null)
            adapter = LanguageAdapter.instance(router);

        script = adapter.compileScript(src);
    }

    @Override
    public String getShortDescription() {
        return "Executes Javascript.";
    }

    @Override
    public String getLongDescription() {
        return removeFinalChar(getShortDescription()) +
               ":<br/><pre style=\"overflow-x:auto\">" +
               escapeHtml4(TextUtil.removeCommonLeadingIndentation(src)) +
               "</pre>";
    }
}