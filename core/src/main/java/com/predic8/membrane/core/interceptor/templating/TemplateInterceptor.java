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
import com.predic8.membrane.core.beautifier.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import com.predic8.membrane.core.resolver.*;
import groovy.text.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.ErrorUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.text.StringEscapeUtils.*;

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
public class TemplateInterceptor extends AbstractInterceptor{

    private static final Logger log = LoggerFactory.getLogger(TemplateInterceptor.class.getName());

    /**
     * @description Path of template file
     */
    private String location;

    private String textTemplate;

    private Template template;

    private String contentType = TEXT_PLAIN;

    private Boolean pretty = false;

    private final JSONBeautifier jsonBeautifier = new JSONBeautifier();

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
            log.warn("Template Exception" + e);
            log.warn("Cause: " + e.getCause());
            createAndSetErrorResponse(exc,500,e.getMessage());
        }
        catch (Exception e) {
            System.out.println(e.getClass());
            createAndSetErrorResponse(exc,500,e.getMessage());
        }

        // Setting Content-Type must come at the end, cause before we want to know what the original type was.
        msg.getHeader().setContentType(getContentType());
        return CONTINUE;
    }

    private String prettifyJson(String text) {
        try {
            return jsonBeautifier.beautify(text);
        } catch (IOException e) {
            return text;
        }
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

    public String getLocation() {
        return location;
    }

    /**
     * @description path of xml template file.
     * @example template.xml
     */
    @MCAttribute
    public void setLocation(String location){
        this.location = location;
    }

    public String getTextTemplate() {
        return textTemplate;
    }

    @MCTextContent
    public void setTextTemplate(String textTemplate) throws IOException, ClassNotFoundException {
        this.textTemplate = textTemplate;

        if(textTemplate != null && !StringUtils.isBlank(textTemplate)){
            template = new StreamingTemplateEngine().createTemplate(this.getTextTemplate());
        }
    }

    public Template getTemplate() {
        return template;
    }

    public void setTemplate(Template template) {
        this.template = template;
    }


    private String getName() {
        return getClass().getAnnotation(MCElement.class).name();
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * @description content type for body
     * @example application/json
     */
    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Boolean getPretty() {
        return pretty;
    }

    /**
     * @description Format JSON documents.
     * @example yes
     * @default no
     */
    @MCAttribute
    public void setPretty(String pretty) {
        this.pretty = Boolean.valueOf(pretty);
    }

    private String formatAsHtml(String plaintext) {
        return String.join("<br />", escapeHtml4(plaintext).split("\n"));
    }

    @Override
    public String getShortDescription() {
        return formatAsHtml(textTemplate);
    }
}