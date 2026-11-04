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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.RegExReplaceInterceptor.TargetType.BODY;
import static com.predic8.membrane.core.interceptor.RegExReplaceInterceptor.TargetType.HEADER;

/**
 * @description <p>Replaces every match of a Java regular expression in the message body or in all header values.</p>
 * <p>The body is decoded first and sent without <code>Content-Encoding</code> afterwards. Messages with a binary
 * content type are left unchanged. If the body cannot be replaced, the flow is aborted with a 500 error.</p>
 * <h3>Security Hint</h3>
 * <p>The expression may run on requests and responses. Overlapping quantifiers such as <code>(.*),(.*)</code> can take
 * a long time on crafted messages. See
 * <a href="https://www.membrane-api.io/security/membrane-redos-protection.html">ReDoS Protection</a>.</p>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - response:
 *         - regExReplacer:
 *             regex: 'http://backend\.internal:8080(/[^"\s]*)'
 *             replace: 'https://api.example.com$1'
 *   target:
 *     url: http://backend.internal:8080
 * </code></pre>
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
            internal(router.getConfiguration().isProduction(),getDisplayName())
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
        res.setBodyContent(res.getBodyAsStringDecoded().replaceAll(regex, replace).getBytes(res.getCharsetOrDefault()));
        res.getHeader().removeFields( CONTENT_ENCODING);
    }

    public String getRegex() {
        return regex;
    }

    /**
     * @description Java regular expression. Every match is replaced.
     * @example http://backend\.internal:8080(/[^"\s]*)
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
     * @description Replacement for each match. <code>$1</code>, <code>$2</code>, ... insert the groups captured by
     * <code>regex</code>; write <code>\$</code> for a literal dollar sign.
     * @example https://api.example.com$1
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
     * @description <code>body</code> replaces in the message body, <code>header</code> in the values of all header
     * fields.
     * @default body
     * @example header
     */
    @MCAttribute
    public void setTarget(TargetType target) {
        this.target = target;
    }

}
