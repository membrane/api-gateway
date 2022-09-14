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
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;
import groovy.text.XmlTemplateEngine;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @description If enabled fills given template from exchange properties and replaces the body. The template can be put between
 * tags or can be loaded from file using location attribute. If the extension of the given file is XML it will use
 * <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_xmltemplateengine">
 * XMLTemplateEngine </a> otherwise <a href="https://docs.groovy-lang.org/docs/next/html/documentation/template-engines.html#_streamingtemplateengine">
 *     StreamingTemplateEngine</a>.
 * @topic 4. Interceptors/Features
 */


@MCElement(name="template", mixed = true)
public class TemplateInterceptor extends AbstractInterceptor{

    /**
     * @description Path of template file
     */
    private String location;

    private String textTemplate;

    private Template template;

    private String contentType;

    public TemplateInterceptor() {
        name = "Template";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        handleInternal(exc.getRequest(), exc);
        return Outcome.CONTINUE;

    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        handleInternal(exc.getResponse(), exc);
        return Outcome.CONTINUE;
    }

    private void handleInternal(Message msg, Exchange exc){
        msg.getHeader().setContentType(getContentType());
        msg.setBodyContent(fillAndGetBytes(exc));
    }

    private byte[] fillAndGetBytes(Exchange exc) {
        return template.make(exc.getProperties()).toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void init() throws Exception {
        if (this.getLocation() != null && !StringUtils.isBlank(this.getTextTemplate())) {
            throw new IllegalStateException("On <" + getName() + ">, ./text() and ./@location cannot be set at the same time.");
        }
        if (location != null) {
            try (InputStreamReader reader = new InputStreamReader(getRouter().getResolverMap()
                    .resolve(ResolverMap.combine(router.getBaseLocation(), location)))) {
                if (FilenameUtils.getExtension(getLocation()).equals("xml")) {
                    template = new XmlTemplateEngine().createTemplate(reader);
                    if (contentType == null) {
                        setContentType("application/xml");
                    }
                }
                else{
                    template = new StreamingTemplateEngine().createTemplate(reader);
                }
                return;
            }
        }
        if(!StringUtils.isBlank(textTemplate)){
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
     */
    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
