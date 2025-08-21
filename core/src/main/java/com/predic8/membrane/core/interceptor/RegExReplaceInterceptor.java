/* Copyright 2011, 2012, 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import java.nio.charset.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.RegExReplaceInterceptor.TargetType.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;

/**
 * @description Runs a regular-expression-replacement on either the message body (default) or all header values.
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "regExReplacer")
public class RegExReplaceInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RegExReplaceInterceptor.class.getName());

    private String regex;
    private String replace;
    private TargetType target = BODY;

    public enum TargetType {
        BODY,
        HEADER
    }

    public RegExReplaceInterceptor() {
        name = "regex replacer";
    }

    @Override
    public String getShortDescription() {
        return "Replaces strings in message header or body using regular expressions.";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, exc.getResponse());
    }

    private Outcome handleInternal(Exchange exc, Message message) {
        if (target == HEADER) {
            replaceHeader(message.getHeader());
            return CONTINUE;
        }

        try {
            replaceBody(message);
        } catch (Exception e) {
            internal(router.isProduction(),getDisplayName())
                    .detail("Could not replace body!")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        return CONTINUE;
    }

    private void replaceHeader(Header header) {
        for (HeaderField hf : header.getAllHeaderFields())
            hf.setValue(hf.getValue().replaceAll(regex, replace));
    }

    private void replaceBody(Message res) {
        if (res.getHeader().isBinaryContentType())
            return;
        log.debug("pattern: {}", regex);
        log.debug("replacement: {}", replace);

        var cs = Charset.forName(requireNonNullElseGet(res.getCharset(), UTF_8::name));
        res.setBodyContent(res.getBodyAsStringDecoded().replaceAll(regex, replace).getBytes(cs));
        res.getHeader().removeFields( CONTENT_ENCODING);
    }

    public String getRegex() {
        return regex;
    }

    /**
     * @description Regex to match against the body.
     * @example Hallo
     */
    @Required
    @MCAttribute
    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getReplace() {
        return replace;
    }

    /**
     * @description String used to replace matched parts.
     * @example Hello
     */
    @Required
    @MCAttribute
    public void setReplace(String replace) {
        this.replace = replace;
    }

    public TargetType getTarget() {
        return target;
    }

    /**
     * @description Whether the replacement should affect the message <tt>body</tt> or the <tt>header</tt> values.
     * Possible values are body and header.
     * @default body
     * @example header
     */
    @MCAttribute
    public void setTarget(TargetType target) {
        this.target = target;
    }

}
