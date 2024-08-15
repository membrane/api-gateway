package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.beautifier.JSONBeautifier;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.resolver.ResolverMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

@MCElement(name = "static", mixed = true)
public class StaticTextInterceptor extends AbstractInterceptor {

    protected String location;

    protected String textTemplate;

    protected String contentType = TEXT_PLAIN;

    protected Boolean pretty = false;

    protected final JSONBeautifier jsonBeautifier = new JSONBeautifier();

    public StaticTextInterceptor() {name = "Static";}

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) {
        msg.setBodyContent(getTemplateBytes());
        msg.getHeader().setContentType(getContentType());
        return CONTINUE;
    }

    private byte @NotNull [] getTemplateBytes() {
        if (pretty)
            switch (contentType) {
            case TEXT_PLAIN:

            }
        if (isOfMediaType(APPLICATION_JSON,contentType) && pretty) {
            return prettifyJson(textTemplate).getBytes(UTF_8);
        }
        return textTemplate.getBytes(UTF_8);
    }

    String prettifyJson(String text) {
        try {
            return jsonBeautifier.beautify(text);
        } catch (IOException e) {
            return text;
        }
    }


    @Override
    public void init() throws Exception {
        if (this.getLocation() != null && (getTextTemplate() != null && !getTextTemplate().isBlank())) {
            throw new IllegalStateException("On <" + getName() + ">, ./text() and ./@location cannot be set at the same time.");
        }

        if (location != null) {
            try (InputStreamReader reader = new InputStreamReader(getRouter().getResolverMap()
                    .resolve(ResolverMap.combine(router.getBaseLocation(), location)))) {

                // @TODO If a file is XML or not is detected based on the Extension. That should
                return;
            }
        }

        throw new IllegalStateException("You have to set either ./@location or ./text()");
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
