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
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.lang.ScriptingUtils;
import com.predic8.membrane.core.resolver.ResolverMap;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;
import groovy.text.TemplateExecutionException;
import groovy.text.XmlTemplateEngine;
import org.apache.commons.io.FilenameUtils;

import java.io.InputStreamReader;
import java.util.HashMap;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description Renders the body content of a message from a template. The template can
 * produce plain text, Json or XML. Variables in the template are substituted with values from the body,
 * header, query parameters, etc. If the extension of a referenced template file is <i>.xml</i> it will use
 * <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_xmltemplateengine">XMLTemplateEngine</a>
 * otherwise <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_streamingtemplateengine">StreamingTemplateEngine</a>.
 * Have a look at the samples in <a href="https://github.com/membrane/service-proxy/tree/master/distribution/examples">examples/template</a>.
 * @topic 4. Interceptors/Features
 */


@MCElement(name="template", mixed = true)
public class TemplateInterceptor extends StaticTextInterceptor{

    private boolean scriptAccessesJson;

    public TemplateInterceptor() {
        name = "Template";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest(), exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse(), exc, RESPONSE);
    }

    private Outcome handleInternal(Message msg, Exchange exc, Flow flow) {
        try {
            msg.setBodyContent(fillAndGetBytes(exc,msg,flow));
        }
        catch (TemplateExecutionException e) {
            exc.setResponse(ProblemDetails.gateway( router.isProduction()).addSubType("template").exception(e).build());
        }
        catch (Exception e) {
            exc.setResponse(ProblemDetails.internal(router.isProduction()).exception(e).build());
        }

        // Setting Content-Type must come at the end, cause before we want to know what the original type was.
        msg.getHeader().setContentType(getContentType());
        return CONTINUE;
    }

    @SuppressWarnings("RedundantThrows") // Declaration of exception is needed. However, Groovy does not declare it.
    private String fillTemplate(Exchange exc, Message msg, Flow flow) throws TemplateExecutionException {

        HashMap<String, Object> binding = ScriptingUtils.createParameterBindings(router.getUriFactory(), exc, msg, flow, scriptAccessesJson && msg.isJSON());
        binding.put("props", binding.get("properties"));
        binding.remove("properties");
        binding.putAll(exc.getProperties()); // To be compatible with old Version

        String payload = template.make(binding).toString();
        if (isOfMediaType(APPLICATION_JSON,contentType) && pretty) {
            return prettifyJson(payload);
        }
        return payload;
    }

    private byte[] fillAndGetBytes(Exchange exc, Message msg, Flow flow) throws TemplateExecutionException {
        return fillTemplate(exc, msg, flow).getBytes(UTF_8);
    }

    @Override
    public void init() throws Exception {
        if (this.getLocation() != null && (getTextTemplate() != null && !getTextTemplate().isBlank())) {
            throw new IllegalStateException("On <" + getName() + ">, ./text() and ./@location cannot be set at the same time.");
        }

        if (location != null) {
            scriptAccessesJson = true; // Workaround. Because the reader is passed we can not look into the template => we put json in if there is one.
            try (InputStreamReader reader = new InputStreamReader(getRouter().getResolverMap()
                    .resolve(ResolverMap.combine(router.getBaseLocation(), location)))) {

                // @TODO If a file is XML or not is detected based on the Extension. That should
                if (FilenameUtils.getExtension(getLocation()).equals("xml")) {
                    template = new XmlTemplateEngine().createTemplate(reader);
                    setContentType(APPLICATION_XML);
                }
                else{
                    template = new StreamingTemplateEngine().createTemplate(reader);
                }
                return;
            }
        }
        if(!textTemplate.isBlank()){
            scriptAccessesJson = textTemplate.contains("json");
            template = new StreamingTemplateEngine().createTemplate(this.getTextTemplate());
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