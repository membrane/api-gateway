/* Copyright 2012,2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.groovy;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.lang.groovy.*;
import com.predic8.membrane.core.util.ConfigurationException;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.util.TextUtil.*;
import static org.apache.commons.text.StringEscapeUtils.*;

/**
 * @description Executes a Groovy script. The script can access and manipulate data from the request and response.
 * Use this or the Javascript plugin to extend the functions of Membrane by scripting.
 * See: example/groovy for working samples
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "groovy", mixed = true)
public class GroovyInterceptor extends AbstractScriptInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GroovyInterceptor.class);

    public GroovyInterceptor() {
        name = "groovy";
    }

    @Override
    public EnumSet<Flow> getFlow() {
        return REQUEST_RESPONSE_ABORT_FLOW;
    }

    @Override
    protected void initInternal() {
        try {
            script = new GroovyLanguageSupport().compileScript(router.getBackgroundInitializer(), null, src);
        } catch (MultipleCompilationErrorsException e) {
            logGroovyError(e);
            throw new ConfigurationException("Error in Groovy script initialization.",e);
        }
    }

    private void logGroovyError(MultipleCompilationErrorsException e) {
        log.error("Error in Groovy script in API '{}' with source: {}", getProxy().getName(),src);
        for(Message error : e.getErrorCollector().getErrors()) {
            ByteArrayOutputStream bais = new ByteArrayOutputStream();
            PrintWriter pw = new PrintWriter(bais);
            error.write(pw);
            pw.flush();
            log.error("Error message: {}",bais);
        }
    }

    @Override
    public String getShortDescription() {
        return "Executes a groovy script.";
    }

    @Override
    public String getLongDescription() {
        return removeFinalChar(getShortDescription()) +
               ":<br/><pre style=\"overflow-x:auto\">" +
               escapeHtml4(src.stripIndent()) +
               "</pre>";
    }
}
