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

package com.predic8.membrane.core.interceptor.xml;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.xml.XMLEncodingUtil;
import org.json.XML;
import org.json.XMLParserConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.json.XMLParserConfiguration.ORIGINAL;

/**
 * @description Converts an XML message body to JSON.
 * <p>
 * The interceptor performs a generic XML-to-JSON transformation using a
 * structural mapping of XML elements and attributes to JSON objects.
 * While this works well for simple and data-oriented XML, it has inherent
 * limitations and challenges.
 * </p>
 *
 * <p>
 * In particular:
 * <ul>
 *   <li>XML attributes and elements are both mapped to JSON properties, which
 *       can lead to ambiguities.</li>
 *   <li>Element order, mixed content, and namespaces may not be preserved
 *       in a meaningful way.</li>
 *   <li>Repeated elements are heuristically converted into JSON arrays,
 *       which may not match the intended domain model.</li>
 * </ul>
 * </p>
 *
 * <p>
 * This interceptor is intended for integration scenarios where XML is used
 * as a transport format and the JSON representation is primarily consumed
 * by applications that do not require full fidelity of the original XML
 * structure.
 * </p>
 *
 * <p>
 * For complex XML schemas or contract-driven integrations, a dedicated
 * transformation using a template, XSLT or a schema-aware mapping is recommended.
 * </p>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "xml2Json")
public class Xml2JsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Xml2JsonInterceptor.class);

    private boolean keepString = false;
    private boolean convertNilAttributeToNull = true;
    private final List<String> forceList = new ArrayList<>();
    private volatile XMLParserConfiguration xmlParserConfig;

    @Override
    public void init() {
        xmlParserConfig = buildParserConfig();
        super.init();
    }

    @Override
    public String getShortDescription() {
        return "Converts XML message bodies to JSON.";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        Message msg = exc.getMessage(flow);
        if (!msg.isXML()) {
            return CONTINUE;
        }
        try {
            msg.setBodyContent(xml2json(getBodyAsString(msg)));
            msg.getHeader().setContentType(APPLICATION_JSON_UTF8);
            return CONTINUE;
        } catch (UnsupportedEncodingException e) {
            handleException(exc, flow, e, "Unsupported encoding: " + e.getMessage());
        } catch (Exception e) {
            handleException(exc, flow, e, null);
        }
        return ABORT;
    }

    private static String getBodyAsString(Message msg) throws IOException {
        if (msg.getHeader().getCharset() != null) return msg.getBodyAsStringDecoded();

        // Conversion is expensive but needed to get encoding from XML
        // because org.json.XML ignores the encoding specified in the XML prolog
        byte[] body = msg.getBody().getContent();
        var fromProlog = XMLEncodingUtil.getEncodingFromXMLProlog(body);
        return new String(body, fromProlog != null ? fromProlog : UTF_8.name());
    }


    private byte[] xml2json(String xml) {
        // In org.json.XML the encoding is skipped, so xml encoding is always ignored: x.skipPast("?>");
        return XML.toJSONObject(xml, xmlParserConfig).toString().getBytes(UTF_8);
    }

    private void handleException(Exchange exc, Flow flow, Exception e, String msg) {
        if (msg == null) {
            msg = "Could not transform XML to JSON: " + e.getMessage();
            log.info(msg, e);
            log.debug("", e);
        }
        internal(router.getConfiguration().isProduction(), getDisplayName()).flow(flow).status(flow == REQUEST ? 400 : 500)
                .detail(msg)
                .exception(e)
                .topLevel("charset-from-header", exc.getMessage(flow).getHeader().getCharset())
                .stacktrace(false)
                .buildAndSetResponse(exc);
    }

    private XMLParserConfiguration buildParserConfig() {
        XMLParserConfiguration cfg = ORIGINAL
                .withKeepStrings(keepString)
                .withConvertNilAttributeToNull(convertNilAttributeToNull);

        if (!forceList.isEmpty())
            cfg = cfg.withForceList(new HashSet<>(forceList));

        return cfg;
    }

    @Override
    public String getDisplayName() {
        return "xml 2 json";
    }


    public boolean isKeepString() {
        return keepString;
    }

    /**
     * @description
     * If true, keeps element text values as Strings instead of trying to coerce them
     * into Number/Boolean types during XML-to-JSON conversion.
     */
    @MCAttribute
    public void setKeepString(boolean keepString) {
        this.keepString = keepString;
    }

    public boolean isConvertNilAttributeToNull() {
        return convertNilAttributeToNull;
    }

    /**
     * @description
     * If true, converts xsi:nil="true" on elements into JSON null values.
     */
    @MCAttribute
    public void setConvertNilAttributeToNull(boolean convertNilAttributeToNull) {
        this.convertNilAttributeToNull = convertNilAttributeToNull;
    }

    public List<String> getForceList() {
        return forceList;
    }

    /**
     * @description
     * Forces the specified element names to be represented as JSON arrays even if they occur only once.
     * @example ["customer", "product"]
     */
    @MCChildElement(allowForeign = true)
    public void setForceList(List<String> forceList) {
        this.forceList.addAll(forceList);
    }
}