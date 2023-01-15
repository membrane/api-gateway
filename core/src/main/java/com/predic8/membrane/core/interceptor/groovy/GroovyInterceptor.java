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
import com.predic8.membrane.core.rules.*;
import org.apache.commons.text.*;
import org.codehaus.groovy.control.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.util.TextUtil.removeCommonLeadingIndentation;
import static com.predic8.membrane.core.util.TextUtil.removeFinalChar;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * @topic 4. Interceptors/Features
 */
@MCElement(name = "groovy", mixed = true)
public class GroovyInterceptor extends AbstractScriptInterceptor {

    Logger log = LoggerFactory.getLogger(GroovyInterceptor.class);

    public GroovyInterceptor() {
        name = "Groovy";
    }

    @Override
    protected void initInternal() {
        try {
            script = new GroovyLanguageSupport().compileScript(router.getBackgroundInitializator(), null, src);
        } catch (MultipleCompilationErrorsException e) {
            logGroovyException(e);
            throw new RuntimeException(e);
        }
    }

    private void logGroovyException(Exception e) {
        try {
            Rule rule = getRule();
            if (rule instanceof ServiceProxy sp) {
                log.error("Exception in Groovy script in service proxy '" + sp.getName() + "' on port " + sp.getPort() + " with path " + (sp.getPath() != null ? sp.getPath().getValue() : "*"));
            } else
                log.error("Exception in Groovy script in service proxy '" + rule.getName() + "'");

            log.error("There is possibly a syntax error in the groovy script (compilation error)");
        } catch (NoSuchElementException e2) {
            //ignore - logging should not break anything
        } finally {
            e.printStackTrace();
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
               escapeHtml4(removeCommonLeadingIndentation(src)) +
               "</pre>";
    }
}
