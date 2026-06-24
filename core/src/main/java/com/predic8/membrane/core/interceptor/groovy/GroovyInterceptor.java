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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.lang.AbstractScriptInterceptor;
import com.predic8.membrane.core.lang.groovy.GroovyLanguageSupport;
import com.predic8.membrane.core.util.ConfigurationException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.EnumSet;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_RESPONSE_ABORT_FLOW;
import static com.predic8.membrane.core.util.text.TextUtil.removeFinalChar;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * @description Runs a Groovy script against the current exchange to read or modify messages and steer the flow. The
 * script runs in the request, response, and abort flows. Its return
 * value decides what happens next:
 * <ul>
 *   <li>an <code>Outcome</code> (<code>CONTINUE</code>, <code>RETURN</code>, <code>ABORT</code>) is used as-is;</li>
 *   <li>a <code>Response</code> is set on the exchange and the flow returns;</li>
 *   <li>a <code>Request</code> replaces the current request;</li>
 *   <li>a <code>Map</code> is serialized to a JSON body;</li>
 *   <li>a <code>String</code> or <code>byte[]</code> becomes the message body;</li>
 *   <li><code>null</code> or no value continues unchanged.</li>
 * </ul>
 * Bindings include <code>exc</code>/<code>exchange</code>, <code>flow</code>, <code>message</code>,
 * <code>header</code>, <code>body</code>, <code>request</code>, <code>response</code>, <code>params</code> (request
 * flow), <code>statusCode</code> (response flow), <code>property</code>, <code>json</code> (when the body is JSON), and
 * <code>spring</code> for bean lookups. Provide the script inline as the element body, or load it from a file with
 * <code>location</code>. See the examples under examples/scripting/groovy and the tutorial
 * tutorials/advanced/70-Scripting-Groovy.yaml.
 * <pre>
 * groovy:
 *   [ location: &lt;file-or-url&gt; ]   # alternative to an inline script
 *   &lt;groovy script&gt;               # element body; omit when location is set
 * </pre>
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - groovy:
 *         src: |
 *           println "Running in the ${flow} flow"
 *           header.add "X-Groovy", "true"
 *           CONTINUE
 * </code></pre>
 */
@MCElement(name = "groovy", mixed = true)
public class GroovyInterceptor extends AbstractScriptInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GroovyInterceptor.class);

    public GroovyInterceptor() {
        name = "groovy";
    }

    @Override
    public EnumSet<Flow> getAppliedFlow() {
        return REQUEST_RESPONSE_ABORT_FLOW;
    }

    @Override
    protected void initInternal() {
        try {
            script = new GroovyLanguageSupport().compileScript(null, src);
        } catch (MultipleCompilationErrorsException e) {
            logGroovyError(e);
            throw new ConfigurationException("Error in Groovy script initialization.",e);
        }
    }

    private void logGroovyError(MultipleCompilationErrorsException e) {
        log.error("Error in Groovy script: {}", src);
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
        return "%s:<br/><pre style=\"overflow-x:auto\">%s</pre>".formatted(removeFinalChar(getShortDescription()), escapeHtml4(src.stripIndent()));
    }
}
