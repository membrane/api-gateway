/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import groovy.lang.*;
import groovy.text.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ScriptingUtils.*;
import static com.predic8.membrane.core.util.FileUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Renders the body content of a message from a template. The template can
 * produce plain text, Json or XML. Variables in the template are substituted with values from the body,
 * header, query parameters, etc. If the extension of a referenced template file is <i>.xml</i> it will use
 * <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_xmltemplateengine">XMLTemplateEngine</a>
 * otherwise <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_streamingtemplateengine">StreamingTemplateEngine</a>.
 * Have a look at the samples in <a href="https://github.com/membrane/api-gateway/tree/master/distribution/examples">examples/template</a>.
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "template", mixed = true)
public class TemplateInterceptor extends AbstractTemplateInterceptor {

    private boolean scriptAccessesJson = false;

    private Template template;

    public TemplateInterceptor() {
        name = "template";
    }

    @Override
    public void init() {
        super.init();
        template = createTemplate();

        // If the template accesses somewhere the json variable make sure it is there
        // You can even access json in an XML or Text Template. See tests.
        scriptAccessesJson = src.contains("json.");
    }

    protected Outcome handleInternal(Exchange exc, Flow flow) {
        try {
            process(exc, flow);
        } catch (GroovyRuntimeException e) {
            log.warn("Groovy error executing template: {}", e.getMessage());
            internal(router.isProduction(), getDisplayName())
                    .addSubSee("groovy")
                    .detail("Groovy error during template rendering.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (TemplateExecutionException tee) {
            ProblemDetails pd = internal(router.isProduction(), getDisplayName())
                    .topLevel("line",tee.getLineNumber())
                    .topLevel("message", tee.getMessage())
                    .addSubSee("template");
            Throwable root = ExceptionUtil.getRootCause(tee);
            if (root instanceof MissingPropertyException mpe) {
                log.warn("{}\n{}" ,root.getMessage(),tee.getMessage());
                pd.detail(root.getMessage())
                        .topLevel("property", mpe.getProperty())
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            if (root instanceof MissingMethodException mme) {
                log.warn("{}\n{}" ,root.getMessage(),tee.getMessage());
                pd.detail(root.getMessage())
                        .topLevel("method", mme.getMethod())
                        .buildAndSetResponse(exc);
                return ABORT;
            }
            log.warn(tee.getMessage());
            pd.exception(tee)
                    .addSubSee("template")
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            log.warn("Error executing template.", e);

            internal(router.isProduction(), getDisplayName())
                    .addSubSee("template")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        return CONTINUE;
    }

    @Override
    protected byte[] getContent(Exchange exchange, Flow flow) {
        // Needed deviation over toString() or Writer class
        return template.make(getVariableBinding(exchange, flow)).toString().getBytes(UTF_8);
    }

    private @NotNull Map<String, Object> getVariableBinding(Exchange exc, Flow flow) {
        return createParameterBindings(router, exc, flow, scriptAccessesJson && isJsonMessage(exc, flow));
    }

    private static boolean isJsonMessage(Exchange exc, Flow flow) {
        return exc.getMessage(flow).isJSON();
    }

    private Template createTemplate() {
        if (src == null)
            throw new ConfigurationException("No template content provided via 'location' or inline text (%s).".formatted(getTemplateLocation()));

        try {
            return createTemplateEngine().createTemplate(new StringReader(src));
        } catch (Exception e) {
            throw new ConfigurationException("Could not create template from " + getTemplateLocation(), e);
        }
    }

    private String getTemplateLocation() {
        return location != null ? location : "inline template";
    }

    private TemplateEngine createTemplateEngine() throws Exception {
        if (location != null) {
            if (isXml(location)) {
                setContentType(APPLICATION_XML);
                return new XmlTemplateEngine();
            }
            if (FileUtil.isJson(location)) {
                setContentType(APPLICATION_JSON);
            }
        }
        return new StreamingTemplateEngine();
    }
}