package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;
import com.predic8.membrane.core.beautifier.JSONBeautifier;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TextUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

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
        if (!pretty)
            return textTemplate.getBytes(UTF_8);

        return switch (contentType) {
            case APPLICATION_JSON -> prettifyJson(textTemplate).getBytes(UTF_8);
            case APPLICATION_XML, APPLICATION_SOAP, TEXT_HTML, TEXT_XML, TEXT_HTML_UTF8, TEXT_XML_UTF8 -> prettifyXML(textTemplate).getBytes(UTF_8);
            default -> trimIndent(textTemplate).getBytes(UTF_8);
        };
    }

    private String prettifyXML(String text) {
        try {
            return TextUtil.formatXML(new StringReader(text));
        } catch (Exception e) {
            log.warn("Failed to format XML", e);
            return text;
        }
    }

    String prettifyJson(String text) {
        try {
            return jsonBeautifier.beautify(text);
        } catch (IOException e) {
            log.warn("Failed to format JSON", e);
            return text;
        }
    }

    static String trimIndent(String multilineString) {
        String[] lines = multilineString.split("\n");
        return trimLines(lines, getMinIndent(lines)).toString().replaceFirst("\\s*$", "");
    }

    private static StringBuilder trimLines(String[] lines, int minIndent) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int currentIndent = line.length() - line.replaceFirst("^\\s+", "").length();
                int effectiveIndent = currentIndent - minIndent;
                result.append(" ".repeat(Math.max(effectiveIndent, 0))).append(line.trim()).append("\n");
            } else {
                result.append("\n");
            }
        }
        return result;
    }

    private static int getMinIndent(String[] lines) {
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                int leadingSpaces = line.length() - line.replaceFirst("^\\s+", "").length();
                minIndent = Math.min(minIndent, leadingSpaces);
            }
        }
        return minIndent;
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
