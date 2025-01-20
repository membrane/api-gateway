/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.xmlcontentfilter;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import javax.xml.xpath.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description <p>
 * The <i>xmlContentFilter</i> removes certain XML elements from message bodies. The elements are described
 * using an XPath expression.
 * </p>
 * @explanation <p>
 * If the XPath expression is simple enough, a StAX-Parser is used to determine whether the XPath might
 * match a message at all. This can improve performance significantly, as a DOM tree does probably not have
 * to to be constructed for every message. This is, for example, the case in <listing name="example"> <src
 * lang="xml"> &lt;xmlContentFilter xPath=&quot;//*[local-name()='Fault' and
 * namespace-uri()='http://schemas.xmlsoap.org/soap/envelope/']//*[local-name()='stacktrace']&quot; /&gt;
 * </src> <caption>xmlContentFilter using a StAX-Parser for improved performance</caption> </listing> where
 * the existence of the &lt;Fault&gt;-element is checked using the StAX-parser before the DOM is
 * constructed.
 * </p>
 * <p>
 * If the message body is not well-formed XML, it is left unchanged. If the message is XOP-encoded, the
 * XPath-expression is run on the reconstituted message; if it matches, the message is replaced by the
 * modified reconstituted message.
 * </p>
 * @topic 3. Enterprise Integration Patterns
 */
@MCElement(name = "xmlContentFilter")
public class XMLContentFilterInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(XMLContentFilterInterceptor.class);

    private String xPath;
    private XMLContentFilter xmlContentFilter;

    public XMLContentFilterInterceptor() {
        name = "xml content filter";
        setFlow(Flow.Set.REQUEST_RESPONSE_ABORT_FLOW);
    }

    @Override
    public String getShortDescription() {
        return "Filters out content from messages through XPath expressions.";
    }

    public String getXPath() {
        return xPath;
    }

    /**
     * @description An XPath 1.0 expression describing the elements to be removed from message bodies.
     */
    @Required
    @MCAttribute
    public void setXPath(String xPath) {
        this.xPath = xPath;
        try {
            this.xmlContentFilter = new XMLContentFilter(xPath);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleMessage(exc, exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleMessage(exc, exc.getResponse());
    }

    private Outcome handleMessage(Exchange exc, Message message) {
        try {
            xmlContentFilter.removeMatchingElements(message);
            return CONTINUE;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            internal(router.isProduction(),getDisplayName())
                    .title("XML Error")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

}
