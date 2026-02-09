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
package com.predic8.membrane.core.interceptor.json;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.lang.AbstractExchangeExpressionInterceptor;
import com.predic8.membrane.core.lang.ExchangeExpression;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.xml.XMLUtil;
import com.predic8.membrane.core.util.xml.XPathUtil;
import com.predic8.membrane.core.util.xml.parser.HardenedXmlParser;
import com.predic8.membrane.core.util.xml.parser.XmlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

import static com.jayway.jsonpath.Configuration.defaultConfiguration;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.JSONPATH;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.XPATH;
import static com.predic8.membrane.core.util.xml.XPathUtil.newXPath;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.xml.transform.OutputKeys.ENCODING;
import static javax.xml.xpath.XPathConstants.NODESET;

/**
 * @description Replaces a JSON value at the configured JSONPath with a static string.
 * @yaml
 * <pre><code>
 *  api:
 *    flow:
 *      - replace:
 *          expression: $.person.name
 *          with: Alice
 * </code></pre>
 */
@SuppressWarnings("unused")
@MCElement(name="replace")
public class ReplaceInterceptor extends AbstractExchangeExpressionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReplaceInterceptor.class);

    private static final XmlParser xmlParser = HardenedXmlParser.getInstance();

    private String with;

    @Override
    public void init() {
        if (!language.equals(JSONPATH) && !language.equals(XPATH)) throw new ConfigurationException("replace.language must be either JSONPATH or XPATH");
        super.init();
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

        switch (language) {
            case JSONPATH -> handleJsonPath(msg, expression);
            case XPATH -> handleXPath(msg, expression);
        }
        return CONTINUE;
    }

    private void handleXPath(Message msg, String xpath) {
        try {
            if (msg.isBodyEmpty() || !msg.isXML()) return;

            var doc = xmlParser.parse(XMLUtil.getInputSource(msg));

            NodeList nodes = (NodeList) newXPath(xmlConfig).evaluate(xpath, doc, NODESET);
            if (nodes == null || nodes.getLength() == 0) return;

            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                if (n instanceof Attr a) {
                    a.setValue(with);
                } else {
                    n.setTextContent(with);
                }
            }

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(ENCODING, UTF_8.name());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(baos));

            msg.setBodyContent(baos.toByteArray());
        } catch (Exception e) {
            log.info("Error replacing via XPath: {}", xpath, e);
        }
    }

    private void handleJsonPath(Message msg, String jsonPath) {
        try {
            if (msg.isBodyEmpty() || !msg.isJSON()) return;

            Object document = defaultConfiguration().jsonProvider().parse(msg.getBodyAsStringDecoded());
            document = JsonPath.parse(document).set(jsonPath, with).json();
            msg.setBodyContent(defaultConfiguration().jsonProvider().toJson(document).getBytes(UTF_8));
        } catch (PathNotFoundException e) {
            log.debug("JSONPath not found: {}", jsonPath);
        } catch (Exception e) {
            log.info("Error replacing via JSONPath: {}", jsonPath, e);
        }
    }

    /**
     * Sets the JSONPath expression to identify the target node in the JSON structure.
     *
     * @param expr the JSONPath expression (e.g., "$.person.name").
     */
    @MCAttribute
    public void setExpression(String expr) {
        expression = expr;
    }

    /**
     * Sets the replacement value for the node specified by the JSONPath.
     *
     * @param with the new value to replace the existing one.
     */
    @MCAttribute
    public void setWith(String with) {
        this.with = with;
    }

    public String getExpression() {return expression;}

    public String getWith() {return with;}


}
