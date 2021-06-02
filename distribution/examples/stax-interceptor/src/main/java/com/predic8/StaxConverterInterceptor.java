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

package com.predic8;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Message;

import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import org.springframework.beans.factory.annotation.Required;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name="staxInterceptor")
public class StaxConverterInterceptor extends AbstractInterceptor implements XMLStreamConstants{
    private String original = "foo";
    private String replace = "bar";


    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) throws Exception {
        if(!msg.isXML()){
            return CONTINUE;
        }
        replaceTag(msg);
        return CONTINUE;
    }



    private void replaceTag(Message res) throws XMLStreamException{
        
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLOutputFactory outFactory = XMLOutputFactory.newInstance();
            XMLEventFactory  eventFactory = XMLEventFactory.newInstance();
            XMLEventReader xmlEventReader = factory.createXMLEventReader(res.getBodyAsStream());
            StringWriter strWriter = new StringWriter();
            XMLEventWriter xmlEventWriter = outFactory.createXMLEventWriter(strWriter);

            while(xmlEventReader.hasNext())
            {
                XMLEvent event = xmlEventReader.nextEvent();
                switch (event.getEventType())
                {
                    case START_ELEMENT:
                        StartElement old = event.asStartElement();
                        event = eventFactory.createStartElement(this.convertQname(old.getName()),
                                old.getAttributes(), old.getNamespaces());
                        break;
                    case END_ELEMENT:
                        EndElement oldEnd = event.asEndElement();
                        event = eventFactory.createEndElement(this.convertQname(oldEnd.getName()),oldEnd.getNamespaces());
                        break;
                }
                xmlEventWriter.add(event);
            }
            res.setBodyContent(strWriter.toString().getBytes(StandardCharsets.UTF_8));
    }

    private QName convertQname(QName old){
        if(old.getLocalPart().equals(getOriginal())){
            return new QName(old.getNamespaceURI(), getReplace(), old.getPrefix());
        }
        
        return old;
    }

    public String getOriginal() {
        return original;
    }


    public void setOriginal(String original) {
        this.original = original;
    }

    public String getReplace() {
        return replace;
    }


    public void setReplace(String replace) {
        this.replace = replace;
    }



}

