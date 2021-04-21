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

package com.predic8.myInterceptor;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class SoapHeaderAdderInterceptor extends AbstractInterceptor{

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if(exc.getRequest().isXML()){
            this.addHeaderNode(exc.getRequest());
        }
        return Outcome.CONTINUE;
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return Outcome.CONTINUE;
    }

    private void addHeaderNode(Message res){
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(res.getBodyAsStream());
            if(!doc.getDocumentElement().getChildNodes().item(1).getNodeName().contains("Header")){
                Element header = doc.createElementNS("http://schemas.xmlsoap.org/soap/envelope", "s11:Header");

                Element security = doc.createElementNS("http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd", "wss:Security");
                security.setAttributeNS(
                        "http://www.w3.org/2000/xmlns/", // namespace
                        "xmlns:wss", // node name including prefix
                        "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" // value
                );

                Element usernameToken = doc.createElement("UsernameToken");
                Element userName = doc.createElement("Username");
                Node userText = doc.createTextNode("root");
                userName.appendChild(userText);

                Element password = doc.createElement("Password");
                password.setAttribute("Type", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest");
                Node passText = doc.createTextNode("cHJlZGljOCBSb2NrcyEK");
                password.appendChild(passText);

                usernameToken.appendChild(userName);
                usernameToken.appendChild(password);

                security.appendChild(usernameToken);
                header.appendChild(security);

                Node oldFirst = doc.getDocumentElement().getChildNodes().item(1);
                doc.getDocumentElement().insertBefore(header, oldFirst);
            }

            res.setBodyContent(docToString(doc).getBytes(StandardCharsets.UTF_8));
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            e.printStackTrace();
        }

    }

    private String docToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString().replaceAll("\n|\r", "");
    }
}