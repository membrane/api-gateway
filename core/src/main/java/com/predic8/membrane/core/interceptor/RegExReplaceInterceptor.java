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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.Required;
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.RegExReplaceInterceptor.TargetType.BODY;
import static com.predic8.membrane.core.interceptor.RegExReplaceInterceptor.TargetType.HEADER;

/**
 * @description Runs a regular-expression-replacement on either the message body (default) or all header values.
 * @topic 4. Interceptors/Features
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
            ProblemDetails.internal(router.isProduction())
                    .component(getDisplayName())
                    .detail("Could not replace body!")
                    .exception(e)
                    .stacktrace(true)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
        return CONTINUE;
    }

    private void replaceHeader(Header header) {
        for (HeaderField hf : header.getAllHeaderFields())
            hf.setValue(hf.getValue().replaceAll(regex, replace));
    }

    private void replaceBody(Message res) throws Exception {
        if (res.getHeader().isBinaryContentType())
            return;
        log.debug("pattern: {}", regex);
        log.debug("replacement: {}", replace);

        res.setBodyContent(res.getBodyAsStringDecoded().replaceAll(regex, replace).getBytes(res.getCharset()));
        res.getHeader().removeFields("Content-Encoding");
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
