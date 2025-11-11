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
import com.predic8.membrane.core.prettifier.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.*;
import groovy.text.*;
import org.apache.commons.io.*;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.resolver.ResolverMap.*;
import static com.predic8.membrane.core.util.StringUtil.*;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.commons.text.StringEscapeUtils.*;

public abstract class AbstractTemplateInterceptor extends AbstractInterceptor {

    protected static final Logger log = LoggerFactory.getLogger(AbstractTemplateInterceptor.class);

    protected String location;
    protected String src;

    protected String contentType = TEXT_PLAIN;

    protected Boolean pretty = false;
    protected Prettifier prettifier = NullPrettifier.INSTANCE;

    protected Charset charset = UTF_8;

    @Override
    public void init() {
        super.init();

        if (getLocation() != null) {
            if (getSrc() != null && !getSrc().isBlank()) {
                throw new ConfigurationException("On <%s>, ./text() and ./@location cannot be set at the same time.".formatted(getName()));
            }
            src = readFromLocation();
        }

        if (pretty)
            prettifier = Prettifier.getInstance(contentType);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal( exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    /**
     * Subclasses may override this method to customize error handling.
     * @param exchange the current Exchange
     * @param flow the message flow (REQUEST or RESPONSE)
     * @return the outcome (CONTINUE or ABORT)
     */
    protected Outcome handleInternal( Exchange exchange, Flow flow) {
        try {
            process(exchange, flow);
            return CONTINUE;
        } catch (Exception e) {
            log.error("Error processing content", e);
            return ABORT;
        }
    }

    /**
     * The processing is fixed:
     * 1. Render Content
     * 2. Prettify (Optional)
     * 3. Set Content-Type
     * @param exchange
     * @param flow
     */
    protected final void process(Exchange exchange, Flow flow) throws TemplateExecutionException {
        Message msg = exchange.getMessage(flow);
        msg.setBodyContent(prettify(getContent(exchange,flow)));
        msg.getHeader().setContentType(contentType);
    }

    protected byte[] prettify(byte[] bytes) {
        try {
            return prettifier.prettify(bytes, charset);
        } catch (Exception e) {
            log.debug("Error beautifying {}. Error: {}",contentType, e.getMessage());
            log.trace("Content: {}", truncateAfter(new String(bytes,charset), 100), e);
            return bytes;
        }
    }

    protected abstract byte[] getContent(Exchange exchange, Flow flow);

    protected String asString(byte[] bytes) {
        return new String(bytes, charset); // Encoding
    }

    private String readFromLocation() {
        try (InputStream is = getRouter().getResolverMap().resolve(combine(getRouter().getBaseLocation(), location))) {
            return IOUtils.toString(is, charset); // TODO Encoding
        } catch (Exception e) {
            throw new ConfigurationException("Could not create template from " + location, e);
        }
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description A file or URL location where the content that should be set as body could be found
     * @default N/A
     * @example conf/body.txt
     */
    @MCAttribute
    public void setLocation(String location){
        this.location = location;
    }

    public String getSrc() {
        return src; // TODO Encoding
    }

    /**
     * @description The content that should be set as body.
     * @default N/A
     * @example { "foo": 1 }
     */
    @MCTextContent
    public void setSrc(String src) {
        this.src = src;
    }

    protected String getName() {
        return getClass().getAnnotation(MCElement.class).name();
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * @description Content-Type of the generated body content.
     * @default text/plain
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
     * @description Format the content of the template. Depending on the contentType the a formatter for JSON, XML or plain text is used.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setPretty(String pretty) {
        this.pretty = Boolean.valueOf(pretty);
    }

    public String getCharset() {
        return charset.name();
    }

    /**
     * @description Encoding of the template text.
     * @default UTF-8
     * @example UTF-16, iso-8859-1
     */
    @MCAttribute
    public void setCharset(String charset) {
        this.charset = Charset.forName(charset);
    }

    @Override
    public String getShortDescription() {
        String s = "Pretty print: %s<br/>".formatted(pretty);
        if (contentType != null) {
            s += "Content-Type: %s<br/>".formatted(contentType);
        }
        return s + formatAsHtml(src);
    }

    protected String formatAsHtml(String plaintext) {
        return String.join("<br/>", escapeHtml4(plaintext).split("\n"));
    }
}