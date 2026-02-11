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
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.groovy.adapted.StreamingTemplateEngine;
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
import static com.predic8.membrane.core.util.text.StringUtil.addLineNumbers;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Renders the body content of a message from a template. The template can
 * produce plain text, Json or XML. Variables in the template are substituted with values from the body,
 * header, query parameters, etc. If the extension of a referenced template file is <i>.xml</i> it will use
 * <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_xmltemplateengine">XMLTemplateEngine</a>
 * otherwise <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_streamingtemplateengine">StreamingTemplateEngine</a>.
 * Have a look at the samples in <a href="https://github.com/membrane/api-gateway/tree/master/distribution/examples/templating">examples/templating</a>.
 *
 * When the <code>contentType</code> is a JSON variant (e.g., <code>application/json</code>), the engine automatically escapes all inserted values. For example, in the
 * <a href="https://github.com/membrane/api-gateway/tree/master/distribution/examples/templating/json">JSON templating example</a>, executing
 * <code>curl "localhost:2000/?answer=20"</code> returns <code>{ "answer" : "20" }</code>. The quotes surrounding the value 20 are added by the auto-escaping mechanism
 * to ensure the output remains a valid string. This feature significantly mitigates security risks by preventing inadvertent JSON injection attacks.
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
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .addSubSee("groovy")
                    .detail("Groovy error during template rendering.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (TemplateExecutionException tee) {
            ProblemDetails pd = internal(router.getConfiguration().isProduction(), getDisplayName())
                    .topLevel("line",tee.getLineNumber())
                    .topLevel("message", tee.getMessage())
                    .stacktrace(false)
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
            log.warn("Root cause: {}\n{}",root.getMessage(),tee.getMessage());
            pd.exception(tee)
                    .detail(root.getMessage())
                    .addSubSee("template")
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            log.warn("Error executing template"  , e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
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
        return createParameterBindings(router, exc, flow, scriptAccessesJson && isJsonMessage(exc, flow), producesJSON());
    }

    private static boolean isJsonMessage(Exchange exc, Flow flow) {
        return exc.getMessage(flow).isJSON();
    }

    private boolean producesJSON() {
        return contentType != null && MimeType.isJson(contentType);
    }

    private Template createTemplate() {
        if (src == null)
            throw new ConfigurationException("No template content provided via 'location' or inline text (%s).".formatted(getTemplateLocation()));

        try {
            return createTemplateEngine().createTemplate(new StringReader(src));
        } catch (Exception e) {
            throw new ConfigurationException("Could not create template from %s:\n\n%s".formatted(getTemplateLocation(), addLineNumbers( src)), e);
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