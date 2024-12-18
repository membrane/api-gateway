/* Copyright 2024 predic8 GmbH, www.predic8.com

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
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.text.StringEscapeUtils.*;

@MCElement(name = "static", mixed = true)
public class StaticInterceptor extends AbstractInterceptor {

    protected String location;

    protected String textTemplate;

    protected String contentType = TEXT_PLAIN;

    protected Boolean pretty = false;

    protected final JSONBeautifier jsonBeautifier = new JSONBeautifier();

    protected static final Logger log = LoggerFactory.getLogger("StaticInterceptor");

    public StaticInterceptor() {
        name = "Static";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest(), exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse(), exc, RESPONSE);
    }

    protected Outcome handleInternal(Message msg, Exchange exchange, Flow flow) throws Exception {
        msg.setBodyContent(getTemplateBytes());
        msg.getHeader().setContentType(getContentType());
        return CONTINUE;
    }

    private byte @NotNull [] getTemplateBytes() {
        if (!pretty)
            return textTemplate.getBytes(UTF_8);

        return switch (contentType) {
            case APPLICATION_JSON -> prettifyJson(textTemplate).getBytes(UTF_8);
            case APPLICATION_XML, APPLICATION_SOAP, TEXT_HTML, TEXT_XML, TEXT_HTML_UTF8, TEXT_XML_UTF8 -> prettifyXML(textTemplate).getBytes(UTF_8);
            default -> unifyIndent(textTemplate).getBytes(UTF_8);
        };
    }

    private String prettifyXML(String text) {
        try {
            return formatXML(new StringReader(text));
        } catch (Exception e) {
            log.warn("Failed to format XML", e);
            return text;
        }
    }

    String prettifyJson(String text) {
        try {
            return jsonBeautifier.beautify(text);
        } catch (IOException e) {
            log.warn("Failed to format JSON: {}", e.getMessage());
            return text;
        }
    }

    @Override
    public void init() throws Exception {
        if (this.getLocation() != null && (getTextTemplate() != null && !getTextTemplate().isBlank())) {
            throw new IllegalStateException("On <" + getName() + ">, ./text() and ./@location cannot be set at the same time.");
        }
    }

    public String getLocation() {
        return location;
    }

    @MCAttribute
    public void setLocation(String location){
        this.location = location;
    }

    public String getTextTemplate() {
        return textTemplate;
    }

    @MCTextContent
    public void setTextTemplate(String textTemplate) {
        this.textTemplate = textTemplate;
    }

    protected String getName() {
        return getClass().getAnnotation(MCElement.class).name();
    }

    public String getContentType() {
        return contentType;
    }

    @MCAttribute
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Boolean getPretty() {
        return pretty;
    }

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
