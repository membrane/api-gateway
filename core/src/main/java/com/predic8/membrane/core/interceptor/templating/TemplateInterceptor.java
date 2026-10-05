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

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.groovy.adapted.StreamingTemplateEngine;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.ExceptionUtil;
import com.predic8.membrane.core.util.FileUtil;
import com.predic8.membrane.core.util.text.SerializationFunction;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import groovy.text.Template;
import groovy.text.TemplateEngine;
import groovy.text.TemplateExecutionException;
import groovy.text.XmlTemplateEngine;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.util.Map;
import java.util.Optional;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ScriptingUtils.createParameterBindings;
import static com.predic8.membrane.core.util.FileUtil.isXml;
import static com.predic8.membrane.core.util.text.SerializationFunction.TEXT_SERIALIZATION;
import static com.predic8.membrane.core.util.text.SerializationUtil.getSerialization;
import static com.predic8.membrane.core.util.text.StringUtil.addLineNumbers;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description Renders the body content of a message from a template. The template can
 * produce plain text, Json or XML. Variables in the template are substituted with values from the body,
 * header, query parameters, etc. If the extension of a referenced template file is <i>.xml</i> it will use
 * <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_xmltemplateengine">XMLTemplateEngine</a>
 * otherwise <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_streamingtemplateengine">StreamingTemplateEngine</a>.
 * <p>See <a href="https://github.com/membrane/api-gateway/blob/master/distribution/tutorials/getting-started/110-Template.yaml">tutorials/getting-started/110-Template.yaml</a>.</p>
 *
 * When the <code>contentType</code> is a JSON variant (e.g., <code>application/json</code>), the engine automatically escapes all inserted values. For example, in the
 * <a href="https://github.com/membrane/api-gateway/tree/master/distribution/examples/templating/json">JSON templating example</a>, executing
 * <code>curl "localhost:2000/?answer=20"</code> returns <code>{ "answer" : "20" }</code>. The quotes surrounding the value 20 are added by the auto-escaping mechanism
 * to ensure the output remains a valid string. This feature significantly mitigates security risks by preventing inadvertent JSON injection attacks.
 *
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - request:
 *         - template:
 *             contentType: application/json
 *             src: |
 *               {
 *                 "name": ${params.name},
 *                 "age": ${params.age}
 *               }
 *     - response:
 *         - template:
 *             location: template.xml
 *     - return: {}
 * </code></pre>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "template", mixed = true)
public class TemplateInterceptor extends AbstractTemplateInterceptor {

    private boolean scriptAccessesJson = false;

    private Template template;

    private SerializationFunction escaping;

    public TemplateInterceptor() {
        name = "template";
    }

    @Override
    public void init() {
        super.init();
        template = createTemplate();

        escaping = getSerialization(contentType).or(() -> Optional.of(TEXT_SERIALIZATION)).get();

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
                    .addSubSee("execution");
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
                    .buildAndSetResponse(exc);
            return ABORT;
        } catch (Exception e) {
            log.warn("Error executing template"  , e);
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .addSubSee("rendering")
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
        return createParameterBindings(router, exc, flow, scriptAccessesJson && isJsonMessage(exc, flow), escaping);
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