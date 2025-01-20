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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.json.*;
import org.slf4j.*;
import org.w3c.dom.*;

import javax.xml.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static javax.xml.transform.OutputKeys.*;


/**
 * @description If enabled converts body content from xml to json.
 * @explanation Can be used for both request and response. Xml file assumed to be in UTF-8. If input is invalid it returns
 * empty json object.
 * @topic 4. Interceptors/Features
 */
@MCElement(name="xml2Json")
public class Xml2JsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Xml2JsonInterceptor.class.getName());

    @Override
    public String getShortDescription() {
        return "Converts XML message bodies to JSON.";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return handleInternal(exc.getRequest());
        } catch (Exception e) {
            log.error("", e);
            internal(router.isProduction(),getDisplayName())
                    .detail("Could not transform XML to JSON!")
                    .internal("flow", "request")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        try {
            return handleInternal(exc.getResponse());
        } catch (Exception e) {
            internal(router.isProduction(),getDisplayName())
                    .detail("Could not return WSDL document!")
                    .internal("flow", "response")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
        }
    }

    private Outcome handleInternal(Message msg) throws Exception {
        if(!msg.isXML()){
            return CONTINUE;
        }

        if(msg.getHeader().getContentEncoding() != null){
            msg.setBodyContent(xml2json(msg.getBodyAsStreamDecoded(), msg.getHeader().getContentEncoding()));
        }
        else{
            msg.setBodyContent(xml2json(loadXMLFromStream(msg.getBodyAsStreamDecoded())));
        }
        msg.getHeader().setContentType(MimeType.APPLICATION_JSON_UTF8);

        return CONTINUE;
    }

    private byte[] xml2json(InputStream body, String encoding) throws UnsupportedEncodingException {
        return XML.toJSONObject(new InputStreamReader(body, encoding)).toString().getBytes(UTF_8);
    }

    private byte[] xml2json(String xml) {
        return XML.toJSONObject(xml).toString().getBytes(UTF_8);
    }

    public static String loadXMLFromStream(InputStream stream) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return documentToString(factory.newDocumentBuilder().parse(stream));
    }

    public static String documentToString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(METHOD, "xml");
            transformer.setOutputProperty(INDENT, "no");
            transformer.setOutputProperty(ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }

    @Override
    public String getDisplayName() {
        return "xml 2 json";
    }
}
