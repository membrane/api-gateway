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
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.resolver.*;
import com.predic8.membrane.core.util.*;
import groovy.lang.*;
import groovy.text.*;
import org.apache.commons.io.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.lang.ScriptingUtils.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Renders the body content of a message from a template. The template can
 * produce plain text, Json or XML. Variables in the template are substituted with values from the body,
 * header, query parameters, etc. If the extension of a referenced template file is <i>.xml</i> it will use
 * <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_xmltemplateengine">XMLTemplateEngine</a>
 * otherwise <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_streamingtemplateengine">StreamingTemplateEngine</a>.
 * Have a look at the samples in <a href="https://github.com/membrane/service-proxy/tree/master/distribution/examples">examples/template</a>.
 * @topic 2. Enterprise Integration Patterns
 */


@MCElement(name="template", mixed = true)
public class TemplateInterceptor extends StaticInterceptor {

    private boolean scriptAccessesJson;

    protected Template template;

    public TemplateInterceptor() {
        name = "template";
    }

    protected Outcome handleInternal(Message msg, Exchange exc, Flow flow) {
        try {
            msg.setBodyContent(fillAndGetBytes(exc,msg,flow));
        }
        catch (TemplateExecutionException e) {
            log.warn("Groovy template error: {}", e.getMessage());
            gateway( router.isProduction(),getDisplayName())
                    .detail("Error during template rendering.")
                    .internal("line", e.getLineNumber())
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        catch (GroovyRuntimeException e) {
            log.warn("Groovy error executing template: {}", e.getMessage());
            internal( router.isProduction(),getDisplayName())
                    .addSubSee("groovy")
                    .detail("Groovy error during template rendering.")
                    .exception(e)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        catch (Exception e) {
            log.warn("", e);
            internal(router.isProduction(),getDisplayName())
                    .addSubSee("template")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }

        // Setting Content-Type must come at the end, cause before we want to know what the original type was.
        msg.getHeader().setContentType(getContentType());
        return CONTINUE;
    }

    @SuppressWarnings("RedundantThrows") // Declaration of exception is needed. However, Groovy does not declare it.
    private String fillTemplate(Exchange exc, Message msg, Flow flow) throws TemplateExecutionException {
        String payload = template.make(getVariableBinding(exc, msg, flow)).toString();
        if (isOfMediaType(APPLICATION_JSON,contentType) && pretty) {
            return prettifyJson(payload);
        }
        return payload;
    }

    private @NotNull HashMap<String, Object> getVariableBinding(Exchange exc, Message msg, Flow flow) {
        return createParameterBindings(router, exc, flow, scriptAccessesJson && msg.isJSON());
    }

    private byte[] fillAndGetBytes(Exchange exc, Message msg, Flow flow) throws TemplateExecutionException {
        return fillTemplate(exc, msg, flow).getBytes(UTF_8);
    }

    @Override
    public void init() {
        super.init();
        if (this.getLocation() != null && (getTextTemplate() != null && !getTextTemplate().isBlank())) {
            throw new IllegalStateException("On <" + getName() + ">, ./text() and ./@location cannot be set at the same time.");
        }

        if (location != null) {
            scriptAccessesJson = true; // Workaround. Because the reader is passed we can not look into the template => we put json in if there is one.
            try (InputStreamReader reader = new InputStreamReader(getRouter().getResolverMap()
                    .resolve(ResolverMap.combine(router.getBaseLocation(), location)))) {

                // If a file is XML or not is detected based on the Extension.
                if (FilenameUtils.getExtension(getLocation()).equals("xml")) {
                    template = new XmlTemplateEngine().createTemplate(reader);
                    setContentType(APPLICATION_XML);
                }
                else{
                    template = new StreamingTemplateEngine().createTemplate(reader);
                }
                return;
            } catch (Exception e) {
                throw new ConfigurationException("Could not create template from " + location,e);
            }
        }
        if(!textTemplate.isBlank()){
            scriptAccessesJson = textTemplate.contains("json");
            try {
                template = new StreamingTemplateEngine().createTemplate(this.getTextTemplate());
            } catch (Exception e) {
                throw new ConfigurationException("Could not create template from " + textTemplate,e);
            }
            return;
        }
        throw new IllegalStateException("You have to set either ./@location or ./text()");
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }
}